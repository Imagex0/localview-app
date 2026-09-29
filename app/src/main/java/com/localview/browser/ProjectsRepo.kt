package com.localview.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * {name, url, port} project repo.
 *
 * SharedPreferences-backed and fully synchronous: writes commit to disk
 * immediately, reads never suspend, so an added project can never be lost
 * to a hung async store. (DataStore was dropped for exactly this failure.)
 */
class ProjectsRepo(context: Context) {

    data class Project(val name: String, val url: String, val port: Int)

    private val prefs = context.getSharedPreferences("localview_projects", Context.MODE_PRIVATE)

    fun load(): List<Project> {
        val raw = prefs.getString(KEY, null) ?: return seed()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Project(o.getString("name"), o.getString("url"), o.getInt("port"))
            }.ifEmpty { seed() }
        }.getOrDefault(seed())
    }

    fun save(projects: List<Project>): Boolean {
        val arr = JSONArray()
        projects.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url).put("port", it.port)) }
        // commit() = synchronous, returns true only when bytes hit disk.
        return prefs.edit().putString(KEY, arr.toString()).commit()
    }

    fun add(name: String, input: String): Project {
        val t = LocalPort.parse(input)
        val p = Project(name.ifBlank { "localhost:${t.port ?: "app"}" }, t.url, t.port ?: 80)
        save(listOf(p) + load().filterNot { it.port == p.port })
        return p
    }

    fun remove(port: Int): Boolean = save(load().filterNot { it.port == port })

    fun clear(): Boolean = prefs.edit().remove(KEY).commit()

    fun count(): Int = load().size

    /** First run: seed defaults AND persist them, so storage exists from day one. */
    private fun seed(): List<Project> {
        val d = listOf(
            Project("Vite React", "http://localhost:5173/", 5173),
            Project("Next.js", "http://localhost:3000/", 3000),
            Project("Python Docs", "http://localhost:8000/", 8000),
        )
        save(d)
        return d
    }

    companion object {
        private const val KEY = "projects_json"
    }
}
