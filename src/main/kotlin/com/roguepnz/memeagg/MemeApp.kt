package com.roguepnz.memeagg

import ch.qos.logback.classic.util.ContextInitializer
import com.roguepnz.memeagg.cluster.NodeService
import com.roguepnz.memeagg.http.KtorController
import com.roguepnz.memeagg.db.Dao
import com.roguepnz.memeagg.db.DaoInitializer
import com.roguepnz.memeagg.http.HttpServerBuilder
import com.roguepnz.memeagg.s3.S3Client
import kotlinx.coroutines.*

fun main() {
    runBlocking {
        System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, "./config/logback.xml")

        DaoInitializer.init(AppContainer.getAll(Dao::class))

        AppContainer.get(S3Client::class).init()

        val nodeService = AppContainer.get(NodeService::class)
        nodeService.start()

        val server = HttpServerBuilder.build(Configs.server, AppContainer.getAll(KtorController::class))
        server.start(true)
    }
}
