package com.roguepnz.memeagg

import com.roguepnz.memeagg.cluster.NodeService
import com.roguepnz.memeagg.cluster.NodeSourceDao
import com.roguepnz.memeagg.core.dao.ContentDao
import com.roguepnz.memeagg.feed.api.FeedController
import com.roguepnz.memeagg.crawler.ContentCrawler
import com.roguepnz.memeagg.crawler.ContentWriter
import com.roguepnz.memeagg.crawler.PayloadDownloader
import com.roguepnz.memeagg.util.UrlDownloader
import com.roguepnz.memeagg.crawler.PayloadUploader
import com.roguepnz.memeagg.s3.S3Client
import com.roguepnz.memeagg.db.MongoDbBuilder
import com.roguepnz.memeagg.http.HttpClientBuilder
import com.roguepnz.memeagg.metrics.MetricsController
import com.roguepnz.memeagg.metrics.MetricsService
import com.roguepnz.memeagg.source.ContentSourceBuilder
import io.ktor.client.HttpClient
import org.litote.kmongo.coroutine.CoroutineDatabase
import kotlin.reflect.KClass
import kotlin.reflect.full.cast


object AppContainer {
    private val components: MutableList<Any> = ArrayList()

    fun <T : Any> get(type: KClass<T>): T {
        val list = getAll(type)
        if (list.isEmpty()) {
            throw IllegalArgumentException("type not found: $type")
        }
        return list[0]
    }

    fun <T : Any> getAll(type: KClass<T>): List<T> {
        return components.asSequence()
            .filter { type.isInstance(it) }
            .map { type.cast(it) }
            .toList()
    }

    private fun put(component: Any) {
        components.add(component)
    }

    init {
        put(MetricsService())
        put(HttpClientBuilder.build(Configs.http))
        put(S3Client(Configs.s3))

        put(PayloadUploader(Configs.crawler, get(S3Client::class), get(MetricsService::class)))

        put(MongoDbBuilder.build())
        put(ContentDao(get(CoroutineDatabase::class)))

        put(ContentSourceBuilder(Configs.sources, get(HttpClient::class), get(CoroutineDatabase::class), get(MetricsService::class)))
        put(ContentWriter(Configs.crawler, get(ContentDao::class), get(MetricsService::class)))

        put(
            ContentCrawler(
                Configs.crawler,
                get(ContentWriter::class),
                get(ContentDao::class),
                get(PayloadUploader::class),
                PayloadDownloader(
                    UrlDownloader(Configs.crawler.maxConcurrentDownloads, get(HttpClient::class)),
                    get(MetricsService::class)
               )
            )
        )

        put(NodeSourceDao(Configs.node, get(CoroutineDatabase::class)))

        put(
            NodeService(
                Configs.node,
                get(NodeSourceDao::class),
                get(ContentSourceBuilder::class),
                get(ContentCrawler::class)
            )
        )

        put(FeedController(get(ContentDao::class)))
        put(MetricsController(get(MetricsService::class)))

    }
}