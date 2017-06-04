package com.nk.tsn.args

import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

fun buildArgsMap(args: Array<String>): Map<String, String> {
    val result = HashMap<String, String>()
    for (arg in args) {
        val split = arg.split("=", limit = 2)
        if (split.size != 2) {
            throw IllegalArgumentException("All functions arguments should be in format 'key=value'")
        }

        result[split[0]] = split[1]
    }

    return result
}

fun <T : Any> initFromArgs(klass: KClass<T>, args: Array<String>): T {
    val primaryConstructor = klass.primaryConstructor ?:
            throw IllegalArgumentException("Primary constructor is expected for $klass")

    val parameters = primaryConstructor.parameters
    if (parameters.isEmpty()) {
        return primaryConstructor.call()
    }

    val argsMap = buildArgsMap(args)
    val paramsMapping = HashMap<KParameter, Any>()
    for (parameter in parameters) {
        val value = argsMap[parameter.name]
        when {
            value != null -> paramsMapping[parameter] = value
            !parameter.isOptional ->
                throw IllegalArgumentException("No value passed for non-option parameter '${parameter.name}' in $klass")
        }
    }

    return primaryConstructor.callBy(paramsMapping)
}