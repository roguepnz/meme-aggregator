package com.roguepnz.memeagg.http

import io.ktor.routing.Routing

typealias RoutingConf = Routing.() -> Unit

interface KtorController {
    fun routing(): RoutingConf
}