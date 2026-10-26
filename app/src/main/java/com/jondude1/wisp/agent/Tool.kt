package com.jondude1.wisp.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// ---- JSON schema helpers -------------------------------------------------------------------

fun jstr(desc: String): JSONObject = JSONObject().put("type", "string").put("description", desc)
fun jint(desc: String): JSONObject = JSONObject().put("type", "integer").put("description", desc)
fun jbool(desc: String): JSONObject = JSONObject().put("type", "boolean").put("description", desc)
fun jenum(desc: String, vararg values: String): JSONObject =
    JSONObject().put("type", "string").put("description", desc).put("enum", JSONArray(values.toList()))

fun jarr(desc: String, item: JSONObject): JSONObject =
    JSONObject().put("type", "array").put("description", desc).put("items", item)

fun jobj(required: List<String>, vararg props: Pair<String, JSONObject>): JSONObject {
    val p = JSONObject()
    props.forEach { p.put(it.first, it.second) }
    return JSONObject().put("type", "object").put("properties", p).put("required", JSONArray(required))
}

// ---- Tool contract -------------------------------------------------------------------------

class ToolContext(
    val context: Context,
    val controller: AgentController,
    val task: AgentTask,
)

class ToolResult(
    val text: String,
    val imageBase64: String? = null,
    val isError: Boolean = false,
)

/**
 * Three tiers, cheapest first. The model is told to prefer them in this order:
 *  1. direct tools (intents, content providers) - fast and reliable
 *  2. screen tools (accessibility) - works in any app, slower and more fragile
 *  3. elevated tools (Shizuku) - planned, not wired yet
 */
interface WispTool {
    val name: String
    val description: String
    val inputSchema: JSONObject

    /** Short present-tense label for the task tray and capsule, e.g. "Opening Chrome". */
    fun stepLabel(input: JSONObject): String = name.replace('_', ' ')

    suspend fun run(input: JSONObject, ctx: ToolContext): ToolResult
}

class ToolRegistry(tools: List<WispTool>) {
    private val byName = tools.associateBy { it.name }
    private val ordered = tools

    fun get(name: String): WispTool? = byName[name]

    fun toJson(withWebSearch: Boolean): JSONArray {
        val arr = JSONArray()
        ordered.forEach {
            arr.put(
                JSONObject()
                    .put("name", it.name)
                    .put("description", it.description)
                    .put("input_schema", it.inputSchema)
            )
        }
        if (withWebSearch) {
            arr.put(
                JSONObject()
                    .put("type", "web_search_20250305")
                    .put("name", "web_search")
                    .put("max_uses", 3)
            )
        }
        return arr
    }
}
