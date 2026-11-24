package com.example.ctrader

import android.util.Log
import com.example.data.model.Candle
import com.example.data.model.TradeRecord
import com.example.data.model.TradingAccount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow
import kotlin.math.roundToLong

enum class CTraderConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHORIZING,
    CONNECTED,
    ERROR,
}

data class CTraderAssetInfo(
    val assetId: Long,
    val name: String,
    val digits: Int = 2,
)

data class CTraderTraderInfo(
    val balance: Double,
    val depositAssetId: Long,
    val depositCurrency: String,
)

data class CTraderSymbolInfo(
    val symbolId: Long,
    val symbolName: String,
    val digits: Int = 5,
    val pipPosition: Int = 4,
    val lotSizeInCents: Long = 10_000_000L, // 100,000 units * 100
    val stepVolumeInCents: Long = 100_000L,
    val minVolumeInCents: Long = 100_000L,
    val maxVolumeInCents: Long = 10_000_000_000L,
    val baseAssetId: Long = 0L,
    val quoteAssetId: Long = 0L,
    val baseCurrency: String = "",
    val quoteCurrency: String = "USD",
)

data class CTraderSpotTick(
    val ctidTraderAccountId: Long,
    val symbolId: Long,
    val symbolName: String,
    val bid: Double?,
    val ask: Double?,
)

sealed interface CTraderStreamEvent {
    data class SpotUpdate(val tick: CTraderSpotTick) : CTraderStreamEvent
    data class ExecutionUpdate(
        val ctidTraderAccountId: Long,
        val executionType: String,
    ) : CTraderStreamEvent
    data class ErrorNotice(val code: String, val description: String) : CTraderStreamEvent
}

class CTraderOpenApiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val msgIdCounter = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val subscribedSymbolIds = ConcurrentHashMap.newKeySet<Long>()
    private val assetsById = ConcurrentHashMap<Long, CTraderAssetInfo>()
    private val symbolsById = ConcurrentHashMap<Long, CTraderSymbolInfo>()
    private val symbolsByName = ConcurrentHashMap<String, CTraderSymbolInfo>()
    private val latestMidPriceBySymbolId = ConcurrentHashMap<Long, Double>()
    private val conversionChainsByAssetPair = ConcurrentHashMap<Pair<Long, Long>, List<CTraderSymbolInfo>>()

    private val _connectionState = MutableStateFlow(CTraderConnectionState.DISCONNECTED)
    val connectionState: StateFlow<CTraderConnectionState> = _connectionState.asStateFlow()

    private val _symbolSpecs = MutableStateFlow<Map<String, CTraderSymbolInfo>>(emptyMap())
    val symbolSpecs: StateFlow<Map<String, CTraderSymbolInfo>> = _symbolSpecs.asStateFlow()

    private val _depositCurrency = MutableStateFlow("USD")
    val depositCurrency: StateFlow<String> = _depositCurrency.asStateFlow()

    private val _depositAssetId = MutableStateFlow(0L)
    val depositAssetId: StateFlow<Long> = _depositAssetId.asStateFlow()

    private val _events = MutableSharedFlow<CTraderStreamEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<CTraderStreamEvent> = _events.asSharedFlow()

    @Volatile
    private var webSocket: WebSocket? = null
    @Volatile
    private var heartbeatJob: Job? = null
    @Volatile
    private var connectedEnv: String = "demo"
    @Volatile
    private var authorizedAccountId: Long = 0L

    fun isConnectedFor(env: String): Boolean {
        return webSocket != null &&
            connectedEnv == env &&
            _connectionState.value == CTraderConnectionState.CONNECTED
    }

    fun getSymbolInfo(symbolName: String): CTraderSymbolInfo? {
        val normalized = symbolName.uppercase()
        return symbolsByName[normalized]
            ?: symbolsByName.entries.firstOrNull { it.key.startsWith(normalized) }?.value
    }

    fun getAvailableSymbolNames(): List<String> = symbolsByName.keys.sorted()

    suspend fun connectAndAuthorizeApp(
        env: String,
        clientId: String,
        clientSecret: String,
    ): Result<Unit> {
        disconnect()
        connectedEnv = env
        _connectionState.value = CTraderConnectionState.CONNECTING

        val url = if (env == "live") LIVE_WS_URL else DEMO_WS_URL
        val openDeferred = CompletableDeferred<Unit>()

        val socket = okHttpClient.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!openDeferred.isCompleted) openDeferred.complete(Unit)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleIncomingMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "WebSocket failure: ${t.message}")
                    if (!openDeferred.isCompleted) openDeferred.completeExceptionally(t)
                    _connectionState.value = CTraderConnectionState.ERROR
                    failAllPending(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!openDeferred.isCompleted) {
                        openDeferred.completeExceptionally(IllegalStateException("Closed: $reason"))
                    }
                    _connectionState.value = CTraderConnectionState.DISCONNECTED
                    failAllPending(IllegalStateException("WebSocket closed"))
                }
            }
        )
        webSocket = socket

        return try {
            withTimeout(15_000L) { openDeferred.await() }
            startHeartbeat()
            _connectionState.value = CTraderConnectionState.AUTHORIZING

            val authRes = sendRequest(
                payloadType = PT_APPLICATION_AUTH_REQ,
                payload = JSONObject().apply {
                    put("clientId", clientId.trim())
                    put("clientSecret", clientSecret.trim())
                },
            )
            if (authRes.optInt("payloadType") == PT_ERROR_RES) {
                val p = authRes.optJSONObject("payload") ?: JSONObject()
                val errCode = p.optNonNullString("errorCode").ifBlank { "AUTH_ERROR" }
                val desc = p.optNonNullString("description")
                throw IllegalStateException(
                    if (desc.isNotBlank()) "$errCode: $desc" else errCode
                )
            }
            _connectionState.value = CTraderConnectionState.CONNECTED
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState.value = CTraderConnectionState.ERROR
            disconnect()
            Result.failure(e)
        }
    }

    suspend fun getAccountsByAccessToken(accessToken: String): Result<List<TradingAccount>> {
        return try {
            val res = sendRequest(
                payloadType = PT_GET_ACCOUNTS_BY_TOKEN_REQ,
                payload = JSONObject().apply {
                    put("accessToken", accessToken.trim())
                },
            )
            checkForError(res)
            val payload = res.optJSONObject("payload") ?: JSONObject()
            val arr = payload.optJSONArray("ctidTraderAccount") ?: JSONArray()
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val ctid = obj.optLong("ctidTraderAccountId", 0L)
                    if (ctid <= 0L) continue
                    val isLive = obj.optBoolean("isLive", false)
                    val login = obj.optLong("traderLogin", ctid)
                    val broker = obj.optNonNullString("brokerTitleShort").ifBlank { "cTrader" }
                    add(
                        TradingAccount(
                            id = "ctrader_$ctid",
                            name = "$broker #$login",
                            broker = broker,
                            accountType = if (isLive) "ctrader_live" else "ctrader_demo",
                            currency = "USD",
                            initialBalance = 0.0,
                            balance = 0.0,
                            apiKeyReference = "cTID $ctid",
                            ctidTraderAccountId = ctid,
                            isLive = isLive,
                            isActive = false,
                        )
                    )
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun authorizeAccount(ctidTraderAccountId: Long, accessToken: String): Result<Unit> {
        return try {
            val res = sendRequest(
                payloadType = PT_ACCOUNT_AUTH_REQ,
                payload = JSONObject().apply {
                    put("ctidTraderAccountId", ctidTraderAccountId)
                    put("accessToken", accessToken.trim())
                },
            )
            checkForError(res)
            authorizedAccountId = ctidTraderAccountId
            subscribedSymbolIds.clear()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAssetsList(ctidTraderAccountId: Long): Result<List<CTraderAssetInfo>> {
        return try {
            val res = sendRequest(
                payloadType = PT_ASSET_LIST_REQ,
                payload = JSONObject().apply {
                    put("ctidTraderAccountId", ctidTraderAccountId)
                },
            )
            checkForError(res)
            val arr = res.optJSONObject("payload")?.optJSONArray("asset") ?: JSONArray()
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optLong("assetId", 0L)
                    val name = obj.optNonNullString("name").uppercase()
                    val digits = obj.optInt("digits", 2)
                    if (id > 0L && name.isNotBlank()) {
                        val info = CTraderAssetInfo(assetId = id, name = name, digits = digits)
                        assetsById[id] = info
                        add(info)
                    }
                }
            }
            val currentDepId = _depositAssetId.value
            if (currentDepId > 0L) {
                assetsById[currentDepId]?.name?.let { _depositCurrency.value = it }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTraderInfo(ctidTraderAccountId: Long): Result<CTraderTraderInfo> {
        return try {
            val res = sendRequest(
                payloadType = PT_TRADER_REQ,
                payload = JSONObject().apply {
                    put("ctidTraderAccountId", ctidTraderAccountId)
                },
            )
            checkForError(res)
            val trader = res.optJSONObject("payload")?.optJSONObject("trader") ?: JSONObject()
            val rawBalance = trader.optLong("balance", 0L)
            val moneyDigits = trader.optInt("moneyDigits", 2).coerceIn(0, 12)
            val balance = rawBalance.toDouble() / 10.0.pow(moneyDigits.toDouble())
            val depAssetId = trader.optLong("depositAssetId", 0L)
            if (depAssetId > 0L) {
                _depositAssetId.value = depAssetId
            }
            val depCurrency = assetsById[depAssetId]?.name?.takeIf { it.isNotBlank() } ?: "USD"
            _depositCurrency.value = depCurrency
            Result.success(CTraderTraderInfo(balance = balance, depositAssetId = depAssetId, depositCurrency = depCurrency))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTraderBalance(ctidTraderAccountId: Long): Result<Double> {
        return fetchTraderInfo(ctidTraderAccountId).map { it.balance }
    }

    suspend fun fetchSymbolsList(ctidTraderAccountId: Long): Result<List<CTraderSymbolInfo>> {
        return try {
            val res = sendRequest(
                payloadType = PT_SYMBOLS_LIST_REQ,
                payload = JSONObject().apply {
                    put("ctidTraderAccountId", ctidTraderAccountId)
                    put("includeArchivedSymbols", false)
                },
            )
            checkForError(res)
            val arr = res.optJSONObject("payload")?.optJSONArray("symbol") ?: JSONArray()
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optLong("symbolId", 0L)
                    val name = obj.optNonNullString("symbolName")
                    val enabled = obj.optBoolean("enabled", true)
                    if (id > 0L && name.isNotBlank() && enabled) {
                        val upperName = name.uppercase()
                        val existing = symbolsById[id]
                        val defaultLot = existing?.lotSizeInCents ?: inferDefaultLotSizeCents(upperName)
                        val defaultStep = existing?.stepVolumeInCents ?: (defaultLot / 100L).coerceAtLeast(1L)
                        val defaultMin = existing?.minVolumeInCents ?: defaultStep
                        val defaultMax = existing?.maxVolumeInCents ?: (defaultLot * 1000L).coerceAtLeast(defaultMin)
                        val baseId = obj.optLong("baseAssetId", existing?.baseAssetId ?: 0L)
                        val quoteId = obj.optLong("quoteAssetId", existing?.quoteAssetId ?: 0L)
                        val baseCur = assetsById[baseId]?.name ?: existing?.baseCurrency.orEmpty()
                        val quoteCur = assetsById[quoteId]?.name
                            ?: existing?.quoteCurrency?.takeIf { it.isNotBlank() }
                            ?: inferQuoteCurrency(upperName)
                        val info = CTraderSymbolInfo(
                            symbolId = id,
                            symbolName = upperName,
                            digits = existing?.digits ?: obj.optInt("digits", inferDefaultDigits(upperName)),
                            pipPosition = existing?.pipPosition ?: obj.optInt("pipPosition", inferDefaultPipPosition(upperName)),
                            lotSizeInCents = defaultLot,
                            stepVolumeInCents = defaultStep,
                            minVolumeInCents = defaultMin,
                            maxVolumeInCents = defaultMax,
                            baseAssetId = baseId,
                            quoteAssetId = quoteId,
                            baseCurrency = baseCur,
                            quoteCurrency = quoteCur,
                        )
                        symbolsById[id] = info
                        symbolsByName[info.symbolName] = info
                        add(info)
                    }
                }
            }
            _symbolSpecs.value = symbolsByName.toMap()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun ensureSymbolDetails(ctidTraderAccountId: Long, symbolId: Long): CTraderSymbolInfo? {
        return try {
            val res = sendRequest(
                payloadType = PT_SYMBOL_BY_ID_REQ,
                payload = JSONObject().apply {
                    put("ctidTraderAccountId", ctidTraderAccountId)
                    put("symbolId", JSONArray().put(symbolId))
                },
            )
            checkForError(res)
            val arr = res.optJSONObject("payload")?.optJSONArray("symbol") ?: return symbolsById[symbolId]
            val obj = arr.optJSONObject(0) ?: return symbolsById[symbolId]
            val existing = symbolsById[symbolId]
            val lotSize = obj.optLong("lotSize", existing?.lotSizeInCents ?: 10_000_000L)
                .coerceAtLeast(100L)
            val defaultStep = (lotSize / 100L).coerceAtLeast(1L)
            val minVol = obj.optLong("minVolume", existing?.minVolumeInCents ?: defaultStep)
                .coerceAtLeast(1L)
            val stepVol = obj.optLong("stepVolume", existing?.stepVolumeInCents ?: defaultStep)
                .coerceAtLeast(1L)
            val maxVol = obj.optLong("maxVolume", existing?.maxVolumeInCents ?: (lotSize * 1000L))
                .coerceAtLeast(minVol)
            val baseId = existing?.baseAssetId ?: obj.optLong("baseAssetId", 0L)
            val quoteId = existing?.quoteAssetId ?: obj.optLong("quoteAssetId", 0L)
            val updated = CTraderSymbolInfo(
                symbolId = symbolId,
                symbolName = existing?.symbolName ?: "SYM_$symbolId",
                digits = obj.optInt("digits", existing?.digits ?: 5),
                pipPosition = obj.optInt("pipPosition", existing?.pipPosition ?: 4),
                lotSizeInCents = lotSize,
                stepVolumeInCents = stepVol,
                minVolumeInCents = minVol,
                maxVolumeInCents = maxVol,
                baseAssetId = baseId,
                quoteAssetId = quoteId,
                baseCurrency = assetsById[baseId]?.name ?: existing?.baseCurrency.orEmpty(),
                quoteCurrency = assetsById[quoteId]?.name ?: existing?.quoteCurrency ?: "USD",
            )
            symbolsById[symbolId] = updated
            symbolsByName[updated.symbolName] = updated
            _symbolSpecs.value = symbolsByName.toMap()
            updated
        } catch (_: Exception) {
            symbolsById[symbolId]
        }
    }

    suspend fun ensureConversionChainSubscribed(
        ctidTraderAccountId: Long,
        quoteAssetId: Long,
        depositAssetId: Long,
    ) {
        if (quoteAssetId <= 0L || depositAssetId <= 0L || quoteAssetId == depositAssetId) return
        val key = quoteAssetId to depositAssetId
        val chain = conversionChainsByAssetPair[key] ?: run {
            val res = runCatching {
                sendRequest(
                    payloadType = PT_SYMBOLS_FOR_CONVERSION_REQ,
                    payload = JSONObject().apply {
                        put("ctidTraderAccountId", ctidTraderAccountId)
                        put("firstAssetId", quoteAssetId)
                        put("lastAssetId", depositAssetId)
                    },
                )
            }.getOrNull() ?: return
            if (res.optInt("payloadType") == PT_ERROR_RES) return
            val arr = res.optJSONObject("payload")?.optJSONArray("symbol") ?: return
            val parsed = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optLong("symbolId", 0L)
                    val sName = obj.optNonNullString("symbolName").uppercase()
                    val bId = obj.optLong("baseAssetId", 0L)
                    val qId = obj.optLong("quoteAssetId", 0L)
                    if (id > 0L) {
                        val existing = symbolsById[id]
                        add(
                            existing?.copy(baseAssetId = bId, quoteAssetId = qId)
                                ?: CTraderSymbolInfo(
                                    symbolId = id,
                                    symbolName = sName.ifBlank { "SYM_$id" },
                                    baseAssetId = bId,
                                    quoteAssetId = qId,
                                )
                        )
                    }
                }
            }
            if (parsed.isNotEmpty()) {
                conversionChainsByAssetPair[key] = parsed
            }
            parsed
        }
        val ids = chain.map { it.symbolId }.filter { it > 0L }
        if (ids.isNotEmpty()) {
            subscribeSpots(ctidTraderAccountId, ids)
        }
    }

    fun computeConversionRateFromChain(quoteAssetId: Long, depositAssetId: Long): Double? {
        if (quoteAssetId <= 0L || depositAssetId <= 0L) return null
        if (quoteAssetId == depositAssetId) return 1.0
        val chain = conversionChainsByAssetPair[quoteAssetId to depositAssetId] ?: return null
        var currentAsset = quoteAssetId
        var rate = 1.0
        for (link in chain) {
            val mid = latestMidPriceBySymbolId[link.symbolId] ?: return null
            if (mid <= 0.0) return null
            when (currentAsset) {
                link.baseAssetId -> {
                    rate *= mid
                    currentAsset = link.quoteAssetId
                }
                link.quoteAssetId -> {
                    rate /= mid
                    currentAsset = link.baseAssetId
                }
                else -> return null
            }
        }
        return if (currentAsset == depositAssetId && rate > 0.0) rate else null
    }

    suspend fun fetchOpenPositions(
        ctidTraderAccountId: Long,
        accountId: String,
        accountName: String,
        isDemo: Boolean,
    ): Result<List<TradeRecord>> {
        return try {
            val res = sendRequest(
                payloadType = PT_RECONCILE_REQ,
                payload = JSONObject().apply {
                    put("ctidTraderAccountId", ctidTraderAccountId)
                },
            )
            checkForError(res)
            val arr = res.optJSONObject("payload")?.optJSONArray("position") ?: JSONArray()
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val pos = arr.optJSONObject(i) ?: continue
                    val posId = pos.optLong("positionId", 0L)
                    if (posId <= 0L) continue
                    val tradeData = pos.optJSONObject("tradeData") ?: JSONObject()
                    val symbolId = tradeData.optLong("symbolId", 0L)
                    val symInfo = symbolsById[symbolId]
                    val symbolName = symInfo?.symbolName ?: "SYM_$symbolId"
                    val lotCents = symInfo?.lotSizeInCents ?: inferDefaultLotSizeCents(symbolName)
                    val volumeCents = tradeData.optLong("volume", 0L)
                    val lots = (volumeCents.toDouble() / lotCents.toDouble()).coerceAtLeast(0.01)
                    val side = parseTradeSide(tradeData.opt("tradeSide"))
                    val entryPrice = pos.optDouble("price", 0.0)
                    val sl = pos.optDouble("stopLoss", 0.0)
                    val tp = pos.optDouble("takeProfit", 0.0)
                    val openTs = tradeData.optLong("openTimestamp", System.currentTimeMillis())

                    add(
                        TradeRecord(
                            id = "ct_pos_$posId",
                            accountId = accountId,
                            accountName = accountName,
                            symbol = symbolName,
                            side = side,
                            lots = (lots * 100.0).roundToLong() / 100.0,
                            entryPrice = entryPrice,
                            exitPrice = 0.0,
                            stopLoss = sl,
                            takeProfit = tp,
                            realizedPnl = 0.0,
                            status = "OPEN",
                            notes = tradeData.optNonNullString("comment").ifBlank { "cTrader #$posId" },
                            isDemo = isDemo,
                            ctraderPositionId = posId,
                            symbolId = symbolId,
                            openedAtMillis = openTs,
                            createdAtMillis = openTs,
                            updatedAtMillis = System.currentTimeMillis(),
                        )
                    )
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTrendbars(
        ctidTraderAccountId: Long,
        symbolId: Long,
        timeframe: String,
        count: Int = 90,
    ): Result<List<Candle>> {
        return try {
            val periodName = mapTimeframeToPeriod(timeframe)
            val periodSec = timeframeSeconds(timeframe)
            val nowMs = System.currentTimeMillis()
            val fromMs = nowMs - (periodSec * (count + 10) * 1000L)

            val res = sendRequest(
                payloadType = PT_GET_TRENDBARS_REQ,
                payload = JSONObject().apply {
                    put("ctidTraderAccountId", ctidTraderAccountId)
                    put("symbolId", symbolId)
                    put("period", periodName)
                    put("fromTimestamp", fromMs)
                    put("toTimestamp", nowMs)
                    put("count", count)
                },
            )
            checkForError(res)
            val arr = res.optJSONObject("payload")?.optJSONArray("trendbar") ?: JSONArray()
            val candles = buildList {
                for (i in 0 until arr.length()) {
                    val bar = arr.optJSONObject(i) ?: continue
                    val lowRaw = bar.optLong("low", 0L)
                    if (lowRaw <= 0L) continue
                    val dOpen = bar.optLong("deltaOpen", 0L)
                    val dHigh = bar.optLong("deltaHigh", 0L)
                    val dClose = bar.optLong("deltaClose", 0L)
                    val mins = bar.optLong("utcTimestampInMinutes", 0L)
                    add(
                        Candle(
                            timeEpochSec = mins * 60L,
                            open = (lowRaw + dOpen) / PRICE_DIVISOR,
                            high = (lowRaw + dHigh) / PRICE_DIVISOR,
                            low = lowRaw / PRICE_DIVISOR,
                            close = (lowRaw + dClose) / PRICE_DIVISOR,
                        )
                    )
                }
            }.sortedBy { it.timeEpochSec }
            Result.success(candles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun subscribeSpots(ctidTraderAccountId: Long, symbolIds: List<Long>): Result<Unit> {
        val toSubscribe = symbolIds.distinct().filter { id ->
            id > 0L && subscribedSymbolIds.add(id)
        }
        if (toSubscribe.isEmpty()) {
            return Result.success(Unit)
        }
        return try {
            val arr = JSONArray().apply { toSubscribe.forEach { put(it) } }
            val res = sendRequest(
                payloadType = PT_SUBSCRIBE_SPOTS_REQ,
                payload = JSONObject().apply {
                    put("ctidTraderAccountId", ctidTraderAccountId)
                    put("symbolId", arr)
                },
            )
            val p = res.optJSONObject("payload")
            if (res.optInt("payloadType") == PT_ERROR_RES &&
                p?.optNonNullString("errorCode") != "ALREADY_SUBSCRIBED"
            ) {
                toSubscribe.forEach { subscribedSymbolIds.remove(it) }
                checkForError(res)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            toSubscribe.forEach { subscribedSymbolIds.remove(it) }
            Result.failure(e)
        }
    }

    suspend fun placeMarketOrder(
        ctidTraderAccountId: Long,
        symbolInfo: CTraderSymbolInfo,
        side: String,
        lots: Double,
        stopLoss: Double = 0.0,
        takeProfit: Double = 0.0,
        referenceEntryPrice: Double = 0.0,
    ): Result<Unit> {
        return try {
            val alignedVolume = alignVolumeToSymbol(lots, symbolInfo)

            val payload = JSONObject().apply {
                put("ctidTraderAccountId", ctidTraderAccountId)
                put("symbolId", symbolInfo.symbolId)
                put("orderType", "MARKET")
                put("tradeSide", if (side.equals("BUY", true)) "BUY" else "SELL")
                put("volume", alignedVolume)
                if (stopLoss > 0.0 && referenceEntryPrice > 0.0) {
                    val roundedSl = roundToDigits(stopLoss, symbolInfo.digits)
                    val dist = kotlin.math.abs(referenceEntryPrice - roundedSl)
                    val relSl = (roundToDigits(dist, symbolInfo.digits) * PRICE_DIVISOR).roundToLong()
                    if (relSl > 0L) {
                        put("relativeStopLoss", relSl)
                    }
                }
                if (takeProfit > 0.0 && referenceEntryPrice > 0.0) {
                    val roundedTp = roundToDigits(takeProfit, symbolInfo.digits)
                    val dist = kotlin.math.abs(roundedTp - referenceEntryPrice)
                    val relTp = (roundToDigits(dist, symbolInfo.digits) * PRICE_DIVISOR).roundToLong()
                    if (relTp > 0L) {
                        put("relativeTakeProfit", relTp)
                    }
                }
                put("comment", "Management Risk and Trade")
            }
            val res = sendRequest(PT_NEW_ORDER_REQ, payload)
            checkForError(res)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun closePosition(
        ctidTraderAccountId: Long,
        positionId: Long,
        symbolInfo: CTraderSymbolInfo?,
        lots: Double,
    ): Result<Unit> {
        return try {
            val volume = if (symbolInfo != null) {
                alignVolumeToSymbol(lots, symbolInfo)
            } else {
                (lots * 10_000_000.0).roundToLong().coerceAtLeast(100L)
            }
            val res = sendRequest(
                payloadType = PT_CLOSE_POSITION_REQ,
                payload = JSONObject().apply {
                    put("ctidTraderAccountId", ctidTraderAccountId)
                    put("positionId", positionId)
                    put("volume", volume)
                },
            )
            checkForError(res)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun amendPositionSlTp(
        ctidTraderAccountId: Long,
        positionId: Long,
        stopLoss: Double,
        takeProfit: Double,
        digits: Int = 5,
    ): Result<Unit> {
        return try {
            val payload = JSONObject().apply {
                put("ctidTraderAccountId", ctidTraderAccountId)
                put("positionId", positionId)
                if (stopLoss > 0.0) put("stopLoss", roundToDigits(stopLoss, digits))
                if (takeProfit > 0.0) put("takeProfit", roundToDigits(takeProfit, digits))
            }
            val res = sendRequest(PT_AMEND_POSITION_SLTP_REQ, payload)
            checkForError(res)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        subscribedSymbolIds.clear()
        authorizedAccountId = 0L
        try {
            webSocket?.close(1000, "User disconnect")
        } catch (_: Exception) {
        }
        webSocket = null
        _connectionState.value = CTraderConnectionState.DISCONNECTED
        failAllPending(IllegalStateException("Disconnected"))
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && webSocket != null) {
                delay(20_000L)
                val hb = JSONObject().apply {
                    put("payloadType", PT_HEARTBEAT_EVENT)
                    put("payload", JSONObject())
                }
                webSocket?.send(hb.toString())
            }
        }
    }

    private suspend fun sendRequest(payloadType: Int, payload: JSONObject): JSONObject {
        val socket = webSocket ?: throw IllegalStateException("Not connected to cTrader server")
        val msgId = "mrt_${msgIdCounter.getAndIncrement()}"
        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[msgId] = deferred

        val envelope = JSONObject().apply {
            put("clientMsgId", msgId)
            put("payloadType", payloadType)
            put("payload", payload)
        }
        val sent = socket.send(envelope.toString())
        if (!sent) {
            pendingRequests.remove(msgId)
            throw IllegalStateException("Failed to send message over WebSocket")
        }

        return try {
            withTimeout(15_000L) { deferred.await() }
        } finally {
            pendingRequests.remove(msgId)
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            val payloadType = json.optInt("payloadType", 0)
            val msgId = json.optNonNullString("clientMsgId")
            val payload = json.optJSONObject("payload") ?: JSONObject()

            if (payloadType == PT_HEARTBEAT_EVENT) return

            if (msgId.isNotBlank()) {
                pendingRequests.remove(msgId)?.complete(json)
            }

            when (payloadType) {
                PT_SPOT_EVENT -> {
                    val symId = payload.optLong("symbolId", 0L)
                    val symInfo = symbolsById[symId] ?: return
                    val rawBid = payload.optLong("bid", 0L)
                    val rawAsk = payload.optLong("ask", 0L)
                    val bid = if (rawBid > 0L) rawBid / PRICE_DIVISOR else null
                    val ask = if (rawAsk > 0L) rawAsk / PRICE_DIVISOR else null
                    val mid = when {
                        bid != null && ask != null -> (bid + ask) / 2.0
                        bid != null -> bid
                        ask != null -> ask
                        else -> null
                    }
                    if (mid != null && mid > 0.0) {
                        latestMidPriceBySymbolId[symId] = mid
                    }
                    if (bid != null || ask != null) {
                        _events.tryEmit(
                            CTraderStreamEvent.SpotUpdate(
                                CTraderSpotTick(
                                    ctidTraderAccountId = payload.optLong("ctidTraderAccountId", 0L),
                                    symbolId = symId,
                                    symbolName = symInfo.symbolName,
                                    bid = bid,
                                    ask = ask,
                                )
                            )
                        )
                    }
                }

                PT_EXECUTION_EVENT -> {
                    _events.tryEmit(
                        CTraderStreamEvent.ExecutionUpdate(
                            ctidTraderAccountId = payload.optLong("ctidTraderAccountId", 0L),
                            executionType = payload.optNonNullString("executionType").ifBlank { "UPDATED" },
                        )
                    )
                }

                PT_ERROR_RES -> {
                    val errCode = payload.optNonNullString("errorCode")
                    if (errCode.isNotBlank() && errCode != "ALREADY_SUBSCRIBED" && msgId.isBlank()) {
                        _events.tryEmit(
                            CTraderStreamEvent.ErrorNotice(
                                code = errCode,
                                description = payload.optNonNullString("description"),
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing cTrader WS frame", e)
        }
    }

    private fun checkForError(res: JSONObject) {
        if (res.optInt("payloadType") == PT_ERROR_RES) {
            val payload = res.optJSONObject("payload") ?: JSONObject()
            val code = payload.optNonNullString("errorCode").ifBlank { "UNKNOWN_ERROR" }
            val desc = payload.optNonNullString("description")
            throw IllegalStateException(if (desc.isNotBlank()) "$code: $desc" else code)
        }
    }

    private fun JSONObject.optNonNullString(key: String): String {
        if (!has(key) || isNull(key)) return ""
        val value = optString(key, "")
        return if (value.equals("null", ignoreCase = true)) "" else value
    }

    private fun failAllPending(t: Throwable) {
        val keys = pendingRequests.keys.toList()
        keys.forEach { key ->
            pendingRequests.remove(key)?.completeExceptionally(t)
        }
    }

    private fun parseTradeSide(raw: Any?): String {
        return when (raw) {
            is Number -> if (raw.toInt() == 2) "SELL" else "BUY"
            is String -> if (raw.equals("SELL", true) || raw == "2") "SELL" else "BUY"
            else -> "BUY"
        }
    }

    private fun mapTimeframeToPeriod(tf: String): String = when (tf.lowercase()) {
        "m1" -> "M1"
        "m5" -> "M5"
        "m15" -> "M15"
        "h1" -> "H1"
        "h4" -> "H4"
        "d1" -> "D1"
        else -> "M1"
    }

    private fun timeframeSeconds(tf: String): Long = when (tf.lowercase()) {
        "m1" -> 60L
        "m5" -> 300L
        "m15" -> 900L
        "h1" -> 3600L
        "h4" -> 14400L
        "d1" -> 86400L
        else -> 60L
    }

    private fun inferQuoteCurrency(symbolName: String): String {
        val clean = symbolName.uppercase().filter { it.isLetter() }
        return if (clean.length >= 6) clean.takeLast(3) else "USD"
    }

    private fun inferDefaultPipPosition(symbolName: String): Int {
        val upper = symbolName.uppercase()
        return when {
            upper.contains("JPY") -> 2
            upper.contains("XAU") || upper.contains("NAS") || upper.contains("US30") || upper.contains("SPX") -> 1
            upper.contains("BTC") || upper.contains("ETH") -> 0
            else -> 4
        }
    }

    private fun inferDefaultLotSizeCents(symbolName: String): Long {
        val upper = symbolName.uppercase()
        return when {
            upper.contains("XAU") -> 10_000L // 100 oz * 100
            upper.contains("XAG") -> 500_000L // 5000 oz * 100
            upper.contains("BTC") || upper.contains("ETH") -> 100L // 1 coin * 100
            upper.contains("US30") || upper.contains("NAS") || upper.contains("SPX") -> 2_000L
            else -> 10_000_000L // 100,000 units * 100
        }
    }

    private fun inferDefaultDigits(symbolName: String): Int {
        val upper = symbolName.uppercase()
        return when {
            upper.contains("JPY") -> 3
            upper.contains("XAU") || upper.contains("BTC") || upper.contains("NAS") -> 2
            else -> 5
        }
    }

    companion object {
        private const val TAG = "CTraderOpenApi"
        const val DEMO_WS_URL = "wss://demo.ctraderapi.com:5036"
        const val LIVE_WS_URL = "wss://live.ctraderapi.com:5036"
        const val PRICE_DIVISOR = 100_000.0

        const val PT_HEARTBEAT_EVENT = 51
        const val PT_APPLICATION_AUTH_REQ = 2100
        const val PT_APPLICATION_AUTH_RES = 2101
        const val PT_ACCOUNT_AUTH_REQ = 2102
        const val PT_ACCOUNT_AUTH_RES = 2103
        const val PT_NEW_ORDER_REQ = 2106
        const val PT_AMEND_POSITION_SLTP_REQ = 2110
        const val PT_CLOSE_POSITION_REQ = 2111
        const val PT_ASSET_LIST_REQ = 2112
        const val PT_SYMBOLS_LIST_REQ = 2114
        const val PT_SYMBOL_BY_ID_REQ = 2116
        const val PT_SYMBOLS_FOR_CONVERSION_REQ = 2118
        const val PT_TRADER_REQ = 2121
        const val PT_RECONCILE_REQ = 2124
        const val PT_EXECUTION_EVENT = 2126
        const val PT_SUBSCRIBE_SPOTS_REQ = 2127
        const val PT_SPOT_EVENT = 2131
        const val PT_GET_TRENDBARS_REQ = 2137
        const val PT_ERROR_RES = 2142
        const val PT_GET_ACCOUNTS_BY_TOKEN_REQ = 2149

        fun alignVolumeToSymbol(lots: Double, symbolInfo: CTraderSymbolInfo): Long {
            val step = symbolInfo.stepVolumeInCents.coerceAtLeast(1L)
            val minVol = symbolInfo.minVolumeInCents.coerceAtLeast(1L)
            val maxVol = symbolInfo.maxVolumeInCents.coerceAtLeast(minVol)
            val rawVolume = kotlin.math.floor(lots * symbolInfo.lotSizeInCents.toDouble() + 1e-3).toLong()
            if (rawVolume <= minVol) return minVol
            val clamped = rawVolume.coerceAtMost(maxVol)
            val steps = (clamped - minVol) / step
            return (minVol + steps * step).coerceIn(minVol, maxVol)
        }

        fun roundToDigits(value: Double, digits: Int): Double {
            val factor = 10.0.pow(digits.coerceIn(0, 8))
            return kotlin.math.round(value * factor) / factor
        }
    }
}
