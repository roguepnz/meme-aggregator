package com.roguepnz.memeagg.crawler

import com.roguepnz.memeagg.core.dao.ContentDao
import com.roguepnz.memeagg.core.model.Content
import com.roguepnz.memeagg.core.model.ContentUpdate
import com.roguepnz.memeagg.source.ContentSource
import com.roguepnz.memeagg.source.config.SourceType
import com.roguepnz.memeagg.source.model.RawContent
import com.roguepnz.memeagg.util.*
import kotlinx.coroutines.*

class ContentCrawler(config: CrawlerConfig,
                     private val writer: ContentWriter,
                     private val contentDao: ContentDao,
                     private val uploader: PayloadUploader,
                     private val downloader: PayloadDownloader) {

    private val logger = loggerFor<ContentCrawler>()

    private class BatchItem(val sourceId: String, val type: SourceType, val raw: RawContent) {
        val rawId = "${sourceId}_${raw.id}"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val batchWorker = BatchWorker(config.crawlerBatchSize, config.crawlerWaitTimeSec, this::handleBatch)

    fun crawl(sourceId: String, type: SourceType, source: ContentSource) {
        scope.launch {
            val channel = source.listen()
            for (raw in channel) {
                batchWorker.add(BatchItem(sourceId, type, raw))
            }
        }
    }

    private suspend fun handleBatch(batch: List<BatchItem>) {
        val keys = batch.map { it.rawId }

        val set = contentDao.containsBySource(keys)

        batch.asSequence()
            .filter { set.contains(it.rawId) }
            .forEach { handleUpdate(it) }

        val new = batch.filter { !set.contains(it.rawId) }
        val seq = contentDao.requestSeq(new.size)

        seq.forEachIndexed { idx, s ->
            scope.launch {
                handleNew(s, new[idx])
            }
        }
    }

    private suspend fun handleUpdate(item: BatchItem) {
        val update = ContentUpdate(
            item.rawId,
            item.raw.likesCount,
            item.raw.dislikesCount,
            item.raw.commentsCount,
            item.raw.rating
        )

        writer.update(update)

        logger.trace("crawl up: ${JSON.stringify(update)}")
    }

    private suspend fun handleNew(seq: Int, item: BatchItem) {
        val raw = item.raw

        val downloadRes = downloader.download(raw.payload.url)
        val url = uploader.upload("${item.rawId}.${raw.payload.extension}" , downloadRes.data, downloadRes.contentType)

        val hash = Hashes.md5(downloadRes.data)

        val order = if (raw.publishTime == 0) seq else raw.publishTime

        val content = Content(
            null,
            seq,
            item.rawId,
            raw.payload.type.code,
            url,
            hash,
            item.type.code,
            item.sourceId,
            raw.publishTime,
            raw.likesCount,
            raw.dislikesCount,
            raw.commentsCount,
            raw.rating,
            raw.author,
            raw.title,
            order
        )

        writer.save(content)

        logger.trace("crawl new: ${JSON.stringify(content)}")
    }
}