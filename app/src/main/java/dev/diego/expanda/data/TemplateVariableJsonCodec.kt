package dev.diego.expanda.data

import org.json.JSONArray
import org.json.JSONObject

/** Small, defensive JSON boundary for named template variables. */
object TemplateVariableJsonCodec {
    fun encode(variables: List<TemplateVariable>): String =
        toJsonArray(variables).toString()

    fun decode(json: String?): List<TemplateVariable> = runCatching {
        if (json.isNullOrBlank()) emptyList() else decode(json.let(::JSONArray))
    }.getOrDefault(emptyList())

    fun toJsonArray(variables: List<TemplateVariable>): JSONArray = JSONArray().apply {
        variables.distinctBy(TemplateVariable::name).forEach { put(toJson(it)) }
    }

    fun decode(array: JSONArray?): List<TemplateVariable> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                val type = item.optString("type").trim()
                if (name.isBlank() || type.isBlank()) continue
                add(
                    TemplateVariable(
                        name = name,
                        type = type,
                        paramsJson = paramsJson(item.opt("params")),
                        dependsOn = stringList(item.optJSONArray("dependsOn"))
                            .ifEmpty { stringList(item.optJSONArray("depends_on")) },
                        injectVars = item.optBoolean("injectVars", item.optBoolean("inject_vars", true)),
                    ),
                )
            }
        }.distinctBy(TemplateVariable::name)
    }

    fun toJson(variable: TemplateVariable): JSONObject = JSONObject().apply {
        put("name", variable.name)
        put("type", variable.type)
        put("params", runCatching { JSONObject(variable.paramsJson) }.getOrElse { JSONObject() })
        put("dependsOn", JSONArray(variable.dependsOn))
        put("injectVars", variable.injectVars)
    }

    private fun paramsJson(value: Any?): String = when (value) {
        is JSONObject -> value.toString()
        is String -> runCatching { JSONObject(value).toString() }.getOrDefault("{}")
        else -> "{}"
    }

    private fun stringList(array: JSONArray?): List<String> = if (array == null) {
        emptyList()
    } else {
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }
}
