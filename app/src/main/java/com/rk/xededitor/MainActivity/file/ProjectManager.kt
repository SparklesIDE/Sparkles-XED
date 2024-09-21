package com.rk.xededitor.MainActivity.file

import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.rk.filetree.interfaces.FileClickListener
import com.rk.filetree.interfaces.FileLongClickListener
import com.rk.filetree.interfaces.FileObject
import com.rk.filetree.model.Node
import com.rk.filetree.provider.file
import com.rk.filetree.widget.DiagonalScrollView
import com.rk.filetree.widget.FileTree
import com.rk.libcommons.After
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.R
import com.rk.xededitor.Settings.Keys
import com.rk.xededitor.Settings.SettingsData
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.MainActivity.MainActivity.Companion.activityRef
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.util.LinkedList
import java.util.Queue

object ProjectManager {

    val projects = HashMap<Int, String>()
    private val queue: Queue<File> = LinkedList()


    fun processQueue(activity: MainActivity) {
        if (activityRef.get() == null){
            activityRef = WeakReference(activity)
        }
        activity.lifecycleScope.launch(Dispatchers.Default) {
            while (queue.isNotEmpty()) {
                delay(100)
                withContext(Dispatchers.Main) {
                    queue.poll()?.let { addProject(activity, it) }
                }

            }
        }

    }

    fun addProject(activity: MainActivity, file: File) {
        if (activityRef == null) {
            activityRef = WeakReference(activity)
        }else if (activityRef!!.get() == null){
            activityRef = WeakReference(activity)
        }

        //BaseActivity.getActivity(MainActivity::class.java)?.let { activity ->
        if (activity.isPaused) {
            queue.add(file)
            return
        }
        val rail = activity.binding.navigationRail
        for (i in 0 until rail.menu.size()) {
            val item = rail.menu.getItem(i)
            val menuItemId = item.itemId
            if (menuItemId != R.id.add_new && !projects.contains(menuItemId)) {
                item.title = file.name
                item.isVisible = true
                item.isChecked = true


                synchronized(projects) {
                    projects[menuItemId] = file.absolutePath
                }


                val fileObj = file(file)

                val fileTree = FileTree(activity)
                fileTree.loadFiles(fileObj)
                fileTree.setOnFileClickListener(fileClickListener)
                fileTree.setOnFileLongClickListener(fileLongClickListener)
                val scrollView =
                    FileTreeScrollViewManager.getFileTreeParentScrollView(activity, fileTree)
                scrollView.id = file.absolutePath.hashCode()

                activity.binding.maindrawer.addView(scrollView)


                changeProject(file, activity)


                //hide + button if 6 projects are added
                if (activity.binding.navigationRail.menu.getItem(5).isVisible) {
                    activity.binding.navigationRail.menu.getItem(6).isVisible = false
                }

                break
            }
        }
        saveProjects(activity)
        // }


    }


    fun removeProject(activity: MainActivity, file: File, saveState: Boolean = true) {
        val filePath = file.absolutePath
        // BaseActivity.getActivity(MainActivity::class.java)?.let { activity ->
        val rail = activity.binding.navigationRail
        for (i in 0 until rail.menu.size()) {
            val item = rail.menu.getItem(i)
            val menuItemId = item.itemId
            if (projects[menuItemId] == filePath) {
                item.isChecked = false
                item.isVisible = false

                for (i in 0 until activity.binding.maindrawer.childCount) {
                    val view = activity.binding.maindrawer.getChildAt(i)
                    if (view is DiagonalScrollView) {
                        if (view.id == file.absolutePath.hashCode()) {
                            activity.binding.maindrawer.removeView(view)
                        }
                    }
                }
                projects.remove(menuItemId)


                if (!rail.menu.getItem(6).isVisible) {
                    rail.menu.getItem(6).isVisible = true
                }

                fun selectItem(itemx: MenuItem) {
                    val previousFilePath = projects[itemx.itemId]
                    if (previousFilePath != null) {
                        itemx.isChecked = true
                        val previousFile = File(previousFilePath)
                        changeProject(previousFile, activity)
                    }
                }

                if (i > 0) {
                    selectItem(rail.menu.getItem(i - 1))
                } else if (i < rail.menu.size() - 1) {
                    selectItem(rail.menu.getItem(i + 1))
                }

                if (saveState) {
                    saveProjects(activity)
                }

                break
            }
        }
        //}
    }


    private var currentProjectId: Int = -1
    fun changeProject(file: File, activity: MainActivity) {
        for (i in 0 until activity.binding.maindrawer.childCount) {
            val view = activity.binding.maindrawer.getChildAt(i)
            if (view is ViewGroup) {
                if (view.id != file.absolutePath.hashCode()) {
                    view.visibility = View.GONE
                } else {
                    view.visibility = View.VISIBLE
                    currentProjectId = file.absolutePath.hashCode()
                }
            }
        }
    }

    fun clear(activity: MainActivity) {
        projects.clear()
        for (i in 0 until activity.binding.maindrawer.childCount) {
            val view = activity.binding.maindrawer.getChildAt(i)
            if (view is DiagonalScrollView) {
                activity.binding.maindrawer.removeView(view)
            }
        }
    }

    fun getSelectedProjectRootFilePath(activity: MainActivity): String? {
        return projects[activity.binding.navigationRail.selectedItemId]
    }

    private fun getSelectedView(activity: MainActivity): FileTree {
        val view: ViewGroup = activity.binding.maindrawer.findViewById(currentProjectId)
        return (view.getChildAt(0) as ViewGroup).getChildAt(0) as FileTree
    }


    object currentProject {
        fun get(activity: MainActivity):File{
            return  File(getSelectedView(activity).getRootFileObject().getAbsolutePath())
        }
        fun refresh(activity: MainActivity) {
            getSelectedView(activity).reloadFileTree()
        }

        fun updateFileRenamed(activity: MainActivity, file: File, newFile: File) {
            getSelectedView(activity).onFileRenamed(file(file), file(newFile))
        }

        fun updateFileDeleted(activity: MainActivity, file: File) {
            getSelectedView(activity).onFileRemoved(file(file))
        }

        fun updateFileAdded(activity: MainActivity, file: File) {
            getSelectedView(activity).onFileAdded(file(file))

        }

    }

    fun changeCurrentProjectRoot(file: FileObject, activity: MainActivity) {
        getSelectedView(activity).loadFiles(file)
    }

    fun restoreProjects(activity: MainActivity) {
        clear(activity)
        val jsonString = SettingsData.getString(Keys.PROJECTS, "")
        if (jsonString.isNotEmpty()) {
            val gson = Gson()
            val projectsList = gson.fromJson(jsonString, Array<String>::class.java)
                .toList()

            projectsList.forEach {
                val file = File(it)
                activity.binding.mainView.visibility = View.VISIBLE
                addProject(activity, file)
            }
        }
    }


    private val fileClickListener = object : FileClickListener {
        override fun onClick(node: Node<FileObject>) {
            if (node.value.isDirectory()) {
                return
            }

                activityRef.get()?.let {
                    if (it.isPaused){
                        println("activity is paused")
                        return@let
                    }
                val loading = LoadingPopup(it, null).show()
                val file = File(node.value.getAbsolutePath())

                //delay 100ms for smoother click
                //opening a file always take more than 500ms because of these delays
                After(100) {
                    rkUtils.runOnUiThread {
                        it.adapter.addFragment(file)
                    }

                    //delay close drawer after 400ms
                    After(400) {
                        if (!SettingsData.getBoolean(Keys.KEEP_DRAWER_LOCKED, false)) {
                            rkUtils.runOnUiThread {
                                it.binding.drawerLayout.close()
                            }
                        }
                        loading.hide()
                    }
                }
            }
        }
    }

    private val fileLongClickListener = object : FileLongClickListener {
        override fun onLongClick(node: Node<FileObject>) {
            activityRef.get()?.apply {
                getSelectedProjectRootFilePath(this)?.let {
                    FileAction(this, File(it), File(node.value.getAbsolutePath()))
                }
            }
        }

    }


    private fun saveProjects(activity: MainActivity) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val gson = Gson()
            val uniqueProjects = projects.values.toSet()
            val jsonString = gson.toJson(uniqueProjects.toList())
            SettingsData.setString(Keys.PROJECTS, jsonString)
        }
    }
}
