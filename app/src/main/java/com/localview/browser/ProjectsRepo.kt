package com.localview.browser

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.store by preferencesDataStore("localview")

/** {name, url, port} project repo. DataStore-backed, no database weight. */
class ProjectsRepo(private val context: Context) {

    data class Project(val name: String, val url: String, val port: Int)

    private val key = stringPreferencesKey("projects")

    suspend fun load(): List<Project> {
        val raw = context.store.data.map { it[key] }.first() ?: return defaults()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Project(o.getString("name"), o.getString("url"), o.getInt("port"))
            }
        }.getOrDefault(defaults())
    }

    suspend fun save(projects: List<Project>) {
        val arr = JSONArray()
        projects.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url).put("port", it.port)) }
        context.store.edit { it[key] = arr.toString() }
    }

    suspend fun add(name: String, input: String): Project {
        val t = LocalPort.parse(input)
        val p = Project(name.ifBlank { "localhost:${t.port ?: "app"}" }, t.url, t.port ?: 80)
        save(listOf(p) + load())
        return p
    }

    suspend fun remove(port: Int) {
        save(load().filterNot { it.port == port })
    }

    private fun defaults() = listOf(
        Project("Vite React", "http://localhost:5173/", 5173),
        Project("Next.js", "http://localhost:3000/", 3000),
        Project("Python Docs", "http://localhost:8000/", 8000),
    )
}
