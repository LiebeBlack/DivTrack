package com.liebeblack.divtrack.lite;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.TextView;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class MainActivity extends Activity {
    private static final String LOG_TAG = "DivTrackLite";
    private static final String PREFERENCES = "lite_rate_cache";
    private static final String KEY_RATE = "official_rate";
    private static final String KEY_SOURCE = "rate_source";
    /** Fecha que la fuente dio al dato (en el BCV, su "Fecha Valor"). */
    private static final String KEY_DATA_AT = "checked_at";
    /** Momento de la última comprobación correcta hecha por la aplicación. */
    private static final String KEY_VERIFIED_AT = "verified_at";

    private static final int STORE_EMPTY = 0;
    private static final int STORE_OK = 1;
    private static final int STORE_INVALID = 2;

    /** Con la tasa comprobada hace menos de esto no se gasta red al abrir la pantalla. */
    private static final long FRESH_CACHE_MS = 10L * 60L * 1000L;
    /** Cadencia de comprobación mientras la pantalla está visible. */
    private static final long VISIBLE_CHECK_INTERVAL_MS = 30L * 60L * 1000L;
    /** La publicación oficial es diaria: pasado un día, el dato se señala como viejo. */
    private static final long STALE_DATA_MS = 24L * 60L * 60L * 1000L;

    private static final ExecutorService networkExecutor = Executors.newSingleThreadExecutor(
            new java.util.concurrent.ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "DivTrackLite-net");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable visibleCheck = new Runnable() {
        @Override
        public void run() {
            if (activityDestroyed) {
                return;
            }
            if (!isCacheFresh()) {
                refreshRate();
            }
            mainHandler.postDelayed(this, VISIBLE_CHECK_INTERVAL_MS);
        }
    };

    private Context appContext;
    private TextView rateValue;
    private TextView checkedAt;
    private TextView lastCheck;
    private TextView status;
    private Button refreshButton;
    private boolean isRefreshing;
    private volatile boolean activityDestroyed;
    private float preferredRateTextSizePx;
    private SharedPreferences cachedPreferences;
    private ViewTreeObserver.OnGlobalLayoutListener pendingLayoutListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appContext = getApplicationContext();
        setContentView(R.layout.activity_main);

        rateValue = findViewById(R.id.rate_value);
        checkedAt = findViewById(R.id.checked_at);
        lastCheck = findViewById(R.id.last_check);
        status = findViewById(R.id.status);
        refreshButton = findViewById(R.id.refresh_button);

        int stored = renderStoredRate();
        if (stored == STORE_OK) {
            showStatus(getString(R.string.status_cached_checked, formatAge(storedVerifiedAt())),
                    isStoredDataStale());
        } else if (stored == STORE_INVALID) {
            showStatus(getString(R.string.status_cache_invalid), false);
        } else {
            showStatus(getString(R.string.status_no_cache), false);
        }

        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshRate();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        startVisibleChecks();
        if (!isCacheFresh()) {
            refreshRate();
        }
    }

    @Override
    protected void onStop() {
        stopVisibleChecks();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        stopVisibleChecks();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void startVisibleChecks() {
        mainHandler.removeCallbacks(visibleCheck);
        mainHandler.postDelayed(visibleCheck, VISIBLE_CHECK_INTERVAL_MS);
    }

    private void stopVisibleChecks() {
        mainHandler.removeCallbacks(visibleCheck);
    }

    private void refreshRate() {
        if (isRefreshing || isFinishing()) {
            return;
        }

        isRefreshing = true;
        refreshButton.setEnabled(false);
        refreshButton.setText(R.string.refreshing);
        showStatus(getString(hasStoredRate() ? R.string.status_cached : R.string.status_starting),
                false);

        networkExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final BcvRateClient.RateQuote quote;
                try {
                    quote = BcvRateClient.fetchOfficialUsdRate();
                } catch (Exception exception) {
                    Log.w(LOG_TAG, "No se pudo actualizar la tasa.", exception);
                    final int messageResource = getRefreshErrorMessage(exception);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!activityDestroyed) {
                                finishRefreshWithError(messageResource);
                            }
                        }
                    });
                    return;
                }

                // Guardar antes de tocar la interfaz: si la actividad se recrea (rotación,
                // pantalla apagada) mientras llega la respuesta, el dato no se pierde.
                final boolean keptFresher = saveQuote(quote);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (activityDestroyed) {
                            return;
                        }
                        if (renderStoredRate() == STORE_OK) {
                            showStatus(getString(
                                            keptFresher
                                                    ? R.string.status_kept_fresher
                                                    : R.string.status_updated),
                                    isStoredDataStale());
                        }
                        finishRefresh();
                    }
                });
            }
        });
    }

    /**
     * Guarda la cotización (seguro desde cualquier hilo) y devuelve true si se conservó la
     * anterior porque su fecha de dato era más reciente: la cifra nunca retrocede.
     */
    private boolean saveQuote(BcvRateClient.RateQuote quote) {
        long storedDataAt = storedDataAt();
        if (hasStoredRate() && storedDataAt > quote.updatedAtMillis) {
            preferences().edit()
                    .putLong(KEY_VERIFIED_AT, System.currentTimeMillis())
                    .apply();
            return true;
        }
        preferences().edit()
                .putString(KEY_RATE, quote.rate.toPlainString())
                .putString(KEY_SOURCE, quote.source)
                .putLong(KEY_DATA_AT, quote.updatedAtMillis)
                .putLong(KEY_VERIFIED_AT, System.currentTimeMillis())
                .apply();
        return false;
    }

    /** Pinta lo guardado. Devuelve STORE_OK, STORE_EMPTY o STORE_INVALID. */
    private int renderStoredRate() {
        String cachedRate = preferences().getString(KEY_RATE, null);
        if (cachedRate == null) {
            clearRateViews();
            return STORE_EMPTY;
        }

        BigDecimal parsedRate;
        try {
            parsedRate = new BigDecimal(cachedRate);
        } catch (NumberFormatException exception) {
            discardStoredRate();
            clearRateViews();
            return STORE_INVALID;
        }
        if (parsedRate.signum() <= 0) {
            discardStoredRate();
            clearRateViews();
            return STORE_INVALID;
        }

        String source = preferences().getString(KEY_SOURCE, getString(R.string.source_bcv));
        long verifiedAt = storedVerifiedAt();
        long dataAt = storedDataAt();
        CharSequence dataDate = dataAt > 0L
                ? formatTimestamp(dataAt)
                : getString(R.string.rate_placeholder);
        rateValue.setText(formatRate(parsedRate));
        checkedAt.setText(getString(R.string.checked_at, source, dataDate));
        lastCheck.setText(verifiedAt > 0L
                ? getString(R.string.last_check, formatTimestamp(verifiedAt))
                : "");
        fitRateTextToWidth();
        return STORE_OK;
    }

    private void clearRateViews() {
        rateValue.setText(R.string.rate_placeholder);
        checkedAt.setText("");
        lastCheck.setText("");
        fitRateTextToWidth();
    }

    private void discardStoredRate() {
        preferences().edit()
                .remove(KEY_RATE)
                .remove(KEY_SOURCE)
                .remove(KEY_DATA_AT)
                .remove(KEY_VERIFIED_AT)
                .apply();
    }

    /** Traduce el fallo a un texto útil mirando toda la cadena de causas, no solo la última. */
    private int getRefreshErrorMessage(Exception exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof javax.net.ssl.SSLException) {
                return R.string.status_secure_connection_failed;
            }
            if (cause instanceof java.net.UnknownHostException) {
                return R.string.status_host_unavailable;
            }
            if (cause instanceof java.net.SocketTimeoutException) {
                return R.string.status_connection_timeout;
            }
            if (cause instanceof BcvRateClient.BcvResponseException
                    || cause instanceof org.json.JSONException
                    || cause instanceof java.text.ParseException) {
                return R.string.status_invalid_response;
            }
            Throwable next = cause.getCause();
            cause = next == cause ? null : next;
        }
        return R.string.status_connection_failed;
    }

    private void finishRefreshWithError(int messageResource) {
        if (isFinishing()) {
            return;
        }
        status.setText(messageResource);
        if (hasStoredRate()) {
            status.append("\n");
            status.append(getString(R.string.status_cache_preserved));
        }
        finishRefresh();
        status.setTextColor(resolveColor(R.color.lite_error));
    }

    private void finishRefresh() {
        isRefreshing = false;
        refreshButton.setEnabled(true);
        refreshButton.setText(R.string.refresh);
    }

    private void showStatus(CharSequence text, boolean warn) {
        status.setText(text);
        status.setTextColor(resolveColor(
                warn ? R.color.lite_warn : R.color.lite_muted));
    }

    private SharedPreferences preferences() {
        if (cachedPreferences == null) {
            cachedPreferences = appContext.getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        }
        return cachedPreferences;
    }

    @SuppressWarnings("deprecation")
    private int resolveColor(int colorRes) {
        return getResources().getColor(colorRes);
    }

    private boolean hasStoredRate() {
        return preferences().contains(KEY_RATE);
    }

    private long storedDataAt() {
        return preferences().getLong(KEY_DATA_AT, 0L);
    }

    private long storedVerifiedAt() {
        return preferences().getLong(KEY_VERIFIED_AT, 0L);
    }

    private boolean isCacheFresh() {
        long verifiedAt = storedVerifiedAt();
        return hasStoredRate()
                && verifiedAt > 0L
                && System.currentTimeMillis() - verifiedAt < FRESH_CACHE_MS;
    }

    private boolean isStoredDataStale() {
        long dataAt = storedDataAt();
        return dataAt > 0L && System.currentTimeMillis() - dataAt > STALE_DATA_MS;
    }

    private String formatAge(long timestampMillis) {
        if (timestampMillis <= 0L) {
            return getString(R.string.age_unknown);
        }
        long minutes = Math.max(0L, (System.currentTimeMillis() - timestampMillis) / 60000L);
        if (minutes < 1L) {
            return getString(R.string.age_now);
        }
        if (minutes < 60L) {
            return getString(R.string.age_minutes, minutes);
        }
        long hours = minutes / 60L;
        if (hours < 48L) {
            return getString(R.string.age_hours, hours);
        }
        return getString(R.string.age_days, hours / 24L);
    }

    @SuppressWarnings("deprecation")
    private void removeLayoutListener(ViewTreeObserver.OnGlobalLayoutListener listener) {
        if (listener == null || !rateValue.getViewTreeObserver().isAlive()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            rateValue.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        } else {
            rateValue.getViewTreeObserver().removeGlobalOnLayoutListener(listener);
        }
    }

    private void fitRateTextToWidth() {
        removeLayoutListener(pendingLayoutListener);
        pendingLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int availableWidth = rateValue.getWidth()
                        - rateValue.getPaddingLeft()
                        - rateValue.getPaddingRight();
                if (availableWidth <= 0) {
                    return;
                }
                removeLayoutListener(this);
                pendingLayoutListener = null;

                float baseSize = preferredRateTextSizePx;
                if (baseSize <= 0f) {
                    baseSize = rateValue.getTextSize();
                    preferredRateTextSizePx = baseSize;
                }
                float measuredWidth = rateValue.getPaint()
                        .measureText(rateValue.getText().toString());
                if (measuredWidth > availableWidth && measuredWidth > 0f) {
                    float adjustedSize = baseSize * availableWidth / measuredWidth;
                    float minimumSize = 24f * getResources()
                            .getDisplayMetrics().scaledDensity;
                    rateValue.setTextSize(
                            android.util.TypedValue.COMPLEX_UNIT_PX,
                            Math.max(minimumSize, adjustedSize));
                } else {
                    rateValue.setTextSize(
                            android.util.TypedValue.COMPLEX_UNIT_PX,
                            baseSize);
                }
            }
        };
        rateValue.getViewTreeObserver().addOnGlobalLayoutListener(pendingLayoutListener);
    }

    private static String formatRate(BigDecimal rate) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("es", "VE"));
        DecimalFormat format = new DecimalFormat("#,##0.0000##", symbols);
        format.setGroupingUsed(false);
        return format.format(rate);
    }

    private static String formatTimestamp(long timestampMillis) {
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("es", "VE"));
        return format.format(new Date(timestampMillis));
    }
}

final class BcvRateClient {
    private static final String BCV_URL = "https://www.bcv.org.ve/";
    private static final String DOLAR_API_URL = "https://ve.dolarapi.com/v1/dolares";
    private static final String EXCHANGE_RATE_API_URL = "https://open.er-api.com/v6/latest/USD";
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_RESPONSE_CHARS = 1_500_000;

    private static final String BCV = "bcv";
    private static final String DOLAR_API = "dolarapi";
    private static final String EXCHANGE_RATE_API = "erapi";

    /** BCV primero; si una fuente falla, su circuito la aparta 10 minutos. */
    private static final ProviderPlan PROVIDER_PLAN =
            new ProviderPlan(BCV, DOLAR_API, EXCHANGE_RATE_API);

    private static final Pattern DOLLAR_SECTION = Pattern.compile(
            "(?is)<[^>]+\\bid\\s*=\\s*['\"]dolar['\"][^>]*>");
    private static final Pattern BCV_DATE = Pattern.compile(
            "(?is)Fecha\\s+Valor:.*?content\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern RATE_IN_STRONG = Pattern.compile(
            "(?is)<strong[^>]*>\\s*([0-9][0-9.,\\s]*)\\s*</strong>");
    private static final Pattern NUMBER = Pattern.compile("[0-9][0-9.,]*");
    private static final Pattern USD_LABEL = Pattern.compile("(?i)\\bUSD\\b");

    private BcvRateClient() {
    }

    static RateQuote fetchOfficialUsdRate() throws java.io.IOException {
        java.io.IOException lastFailure = null;
        long now = System.currentTimeMillis();
        for (String provider : PROVIDER_PLAN.orderFor(now)) {
            try {
                RateQuote quote = fetchFrom(provider);
                PROVIDER_PLAN.recordSuccess(provider);
                return quote;
            } catch (java.io.IOException exception) {
                Log.w("DivTrackLite", "Fuente " + provider + " no respondió: "
                        + exception.getMessage());
                PROVIDER_PLAN.recordFailure(provider, System.currentTimeMillis());
                lastFailure = exception;
            }
        }
        throw new java.io.IOException(
                "Ninguna fuente de tasas respondió; se conserva la última tasa guardada.",
                lastFailure);
    }

    private static RateQuote fetchFrom(String provider) throws java.io.IOException {
        if (BCV.equals(provider)) {
            return fetchBcvRate();
        }
        if (DOLAR_API.equals(provider)) {
            return fetchDolarApiRate();
        }
        return fetchExchangeRateApiRate();
    }

    private static RateQuote fetchBcvRate() throws java.io.IOException {
        String html = readHttps(BCV_URL, "www.bcv.org.ve", "text/html");
        BigDecimal rate = parseDollarRate(html);
        long updatedAt = System.currentTimeMillis();
        Matcher dateMatcher = BCV_DATE.matcher(html);
        if (dateMatcher.find()) {
            Long publishedAt = parseIsoTimestamp(dateMatcher.group(1));
            if (publishedAt != null) {
                updatedAt = publishedAt;
            }
        }
        return new RateQuote(rate, "BCV", updatedAt);
    }

    private static RateQuote fetchDolarApiRate() throws java.io.IOException {
        String body = readHttps(DOLAR_API_URL, "ve.dolarapi.com", "application/json");
        try {
            org.json.JSONArray rates = new org.json.JSONArray(body);
            for (int index = 0; index < rates.length(); index++) {
                org.json.JSONObject item = rates.optJSONObject(index);
                if (item == null
                        || !"oficial".equalsIgnoreCase(item.optString("fuente"))
                        || !"USD".equalsIgnoreCase(item.optString("moneda"))) {
                    continue;
                }

                BigDecimal rate = firstPositiveValue(item, "promedio", "venta", "compra");
                if (rate == null) {
                    throw new BcvResponseException("DolarAPI no publicó una tasa oficial utilizable.");
                }
                Long publishedAt = parseIsoTimestamp(item.optString("fechaActualizacion", ""));
                return new RateQuote(
                        rate,
                        "DolarAPI (respaldo)",
                        publishedAt != null ? publishedAt : System.currentTimeMillis());
            }
            throw new BcvResponseException("DolarAPI no devolvió el dólar oficial.");
        } catch (org.json.JSONException exception) {
            throw new BcvResponseException("La respuesta JSON de DolarAPI no es válida.", exception);
        }
    }

    private static RateQuote fetchExchangeRateApiRate() throws java.io.IOException {
        String body = readHttps(EXCHANGE_RATE_API_URL, "open.er-api.com", "application/json");
        try {
            org.json.JSONObject response = new org.json.JSONObject(body);
            if (!"success".equalsIgnoreCase(response.optString("result"))
                    || !"USD".equalsIgnoreCase(response.optString("base_code"))) {
                throw new BcvResponseException("ER-API no devolvió una tasa USD válida.");
            }

            org.json.JSONObject rates = response.optJSONObject("rates");
            BigDecimal rate = rates == null ? null : firstPositiveValue(rates, "VES");
            if (rate == null) {
                throw new BcvResponseException("ER-API no publicó el par USD/VES.");
            }

            long updatedAt = response.optLong("time_last_update_unix", 0L) * 1000L;
            if (updatedAt <= 0L) {
                updatedAt = System.currentTimeMillis();
            }
            return new RateQuote(rate, "ER-API (respaldo)", updatedAt);
        } catch (org.json.JSONException exception) {
            throw new BcvResponseException("La respuesta JSON de ER-API no es válida.", exception);
        }
    }

    private static BigDecimal firstPositiveValue(
            org.json.JSONObject object,
            String... fieldNames) throws java.io.IOException {
        for (String fieldName : fieldNames) {
            Object rawValue;
            try {
                rawValue = object.opt(fieldName);
            } catch (RuntimeException exception) {
                throw new BcvResponseException("No se pudo leer el campo " + fieldName + ".", exception);
            }
            if (rawValue == null || rawValue == org.json.JSONObject.NULL) {
                continue;
            }
            try {
                BigDecimal value = new BigDecimal(rawValue.toString());
                if (value.signum() > 0) {
                    return value;
                }
            } catch (NumberFormatException exception) {
                throw new BcvResponseException("El campo " + fieldName + " no es un número.", exception);
            }
        }
        return null;
    }

    private static boolean isAllowedHost(String host, String expectedHost) {
        if (host == null || expectedHost == null) {
            return false;
        }
        if (host.equalsIgnoreCase(expectedHost)) {
            return true;
        }
        if (expectedHost.startsWith("www.") && host.equalsIgnoreCase(expectedHost.substring(4))) {
            return true;
        }
        if (host.startsWith("www.") && host.substring(4).equalsIgnoreCase(expectedHost)) {
            return true;
        }
        return false;
    }

    private static String readHttps(String address, String expectedHost, String accept)
            throws java.io.IOException {
        javax.net.ssl.HttpsURLConnection connection = null;
        java.io.InputStream input = null;
        java.io.Reader reader = null;

        try {
            java.net.URL url = new java.net.URL(address);
            if (!"https".equalsIgnoreCase(url.getProtocol())
                    || !isAllowedHost(url.getHost(), expectedHost)) {
                throw new BcvResponseException("Endpoint HTTPS no autorizado.");
            }
            connection = (javax.net.ssl.HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", accept);
            connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            // Sin Accept-Encoding propio la plataforma pide gzip y descomprime sola:
            // la página del BCV pasa de ~150 KB a ~30 KB.
            if (Build.VERSION.SDK_INT >= 14 && Build.VERSION.SDK_INT < 21) {
                connection.setSSLSocketFactory(new Tls12SocketFactory(
                        (SSLSocketFactory) SSLSocketFactory.getDefault()));
            }

            int responseCode = connection.getResponseCode();
            java.net.URL finalUrl = connection.getURL();
            if (!"https".equalsIgnoreCase(finalUrl.getProtocol())
                    || !isAllowedHost(finalUrl.getHost(), expectedHost)) {
                throw new BcvResponseException("La respuesta salió del host HTTPS permitido.");
            }
            if (responseCode != 200) {
                throw new BcvResponseException(
                        "El proveedor respondió HTTP " + responseCode + ".");
            }

            input = connection.getInputStream();
            reader = new java.io.InputStreamReader(input, "UTF-8");
            return readBounded(reader);
        } finally {
            closeQuietly(reader);
            closeQuietly(input);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readBounded(java.io.Reader reader) throws java.io.IOException {
        StringBuilder content = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            if (content.length() + count > MAX_RESPONSE_CHARS) {
                throw new java.io.IOException("La página del BCV supera el límite esperado.");
            }
            content.append(buffer, 0, count);
        }
        return content.toString();
    }

    private static BigDecimal parseDollarRate(String html) throws java.io.IOException {
        Matcher sectionMatcher = DOLLAR_SECTION.matcher(html);
        if (!sectionMatcher.find()) {
            throw new BcvResponseException("No se encontró la sección USD del BCV.");
        }

        int sectionStart = sectionMatcher.end();
        String dollarSection = html.substring(sectionStart);
        Matcher usdMatcher = USD_LABEL.matcher(dollarSection);
        if (!usdMatcher.find()) {
            throw new BcvResponseException("La sección del BCV no identifica USD.");
        }

        Matcher rateMatcher = RATE_IN_STRONG.matcher(dollarSection.substring(usdMatcher.end()));
        if (!rateMatcher.find()) {
            throw new BcvResponseException("No se encontró el valor USD publicado por el BCV.");
        }
        String candidate = rateMatcher.group(1).replaceAll("\\s+", "");
        if (!NUMBER.matcher(candidate).matches()) {
            throw new BcvResponseException("El valor USD del BCV tiene un formato no válido.");
        }
        BigDecimal rate = parseDecimal(candidate);
        if (rate.signum() <= 0) {
            throw new BcvResponseException("El valor USD del BCV debe ser mayor que cero.");
        }
        return rate;
    }

    private static Long parseIsoTimestamp(String timestamp) {
        if (timestamp == null || timestamp.length() < 19) {
            return null;
        }
        String normalized = timestamp.trim().replaceFirst("([+-][0-9]{2}):([0-9]{2})$", "$1$2");
        if (normalized.endsWith("Z")) {
            normalized = normalized.substring(0, normalized.length() - 1) + "+0000";
        }
        String[] formats = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
        };
        for (String formatString : formats) {
            SimpleDateFormat format = new SimpleDateFormat(formatString, Locale.US);
            if (!formatString.endsWith("Z")) {
                format.setTimeZone(java.util.TimeZone.getTimeZone("America/Caracas"));
            }
            format.setLenient(false);
            java.text.ParsePosition position = new java.text.ParsePosition(0);
            Date parsed = format.parse(normalized, position);
            if (parsed != null && position.getIndex() == normalized.length()) {
                return parsed.getTime();
            }
        }
        return null;
    }

    private static BigDecimal parseDecimal(String raw) throws java.io.IOException {
        String value = raw.replace(" ", "");
        int comma = value.lastIndexOf(',');
        int dot = value.lastIndexOf('.');

        if (comma >= 0 && dot >= 0) {
            if (comma > dot) {
                value = value.replace(".", "").replace(',', '.');
            } else {
                value = value.replace(",", "");
            }
        } else if (comma >= 0) {
            value = value.replace(',', '.');
        }

        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new BcvResponseException("El valor USD del BCV no se pudo interpretar.",
                    exception);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (java.io.IOException ignored) {
        }
    }

    static final class BcvResponseException extends java.io.IOException {
        BcvResponseException(String message) {
            super(message);
        }

        BcvResponseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class RateQuote {
        final BigDecimal rate;
        final String source;
        final long updatedAtMillis;

        RateQuote(BigDecimal rate, String source, long updatedAtMillis) {
            this.rate = rate;
            this.source = source;
            this.updatedAtMillis = updatedAtMillis;
        }
    }

    private static final class Tls12SocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        Tls12SocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public java.net.Socket createSocket() throws java.io.IOException {
            return enableModernTls(delegate.createSocket());
        }

        @Override
        public java.net.Socket createSocket(
                java.net.Socket socket,
                String host,
                int port,
                boolean autoClose) throws java.io.IOException {
            return enableModernTls(delegate.createSocket(socket, host, port, autoClose));
        }

        @Override
        public java.net.Socket createSocket(String host, int port) throws java.io.IOException {
            return enableModernTls(delegate.createSocket(host, port));
        }

        @Override
        public java.net.Socket createSocket(
                String host,
                int port,
                java.net.InetAddress localHost,
                int localPort) throws java.io.IOException {
            return enableModernTls(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public java.net.Socket createSocket(java.net.InetAddress host, int port)
                throws java.io.IOException {
            return enableModernTls(delegate.createSocket(host, port));
        }

        @Override
        public java.net.Socket createSocket(
                java.net.InetAddress address,
                int port,
                java.net.InetAddress localAddress,
                int localPort) throws java.io.IOException {
            return enableModernTls(delegate.createSocket(address, port, localAddress, localPort));
        }

        private java.net.Socket enableModernTls(java.net.Socket socket) {
            if (!(socket instanceof SSLSocket)) {
                return socket;
            }
            SSLSocket sslSocket = (SSLSocket) socket;
            java.util.ArrayList<String> enabledProtocols = new java.util.ArrayList<String>();
            for (String protocol : sslSocket.getSupportedProtocols()) {
                if ("TLSv1.2".equals(protocol) || "TLSv1.3".equals(protocol)) {
                    enabledProtocols.add(protocol);
                }
            }
            if (!enabledProtocols.isEmpty()) {
                sslSocket.setEnabledProtocols(enabledProtocols.toArray(new String[0]));
            }
            return sslSocket;
        }
    }
}
