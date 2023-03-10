package com.verodigital.androidtask.presentation.ui.fragments

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.verodigital.androidtask.R
import com.verodigital.androidtask.data.datasource.Task
import com.verodigital.androidtask.domain.TaskListViewModel
import com.verodigital.androidtask.presentation.ui.adapters.TaskAdapter
import com.verodigital.androidtask.util.PermissionUtil
import com.verodigital.androidtask.util.getProgressDrawable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions

@AndroidEntryPoint
class MainFragment : Fragment(R.layout.fragment_main), EasyPermissions.PermissionCallbacks {
    private val taskListViewModel: TaskListViewModel by viewModels()
    private val taskAdapter: TaskAdapter = TaskAdapter(arrayListOf())
    private var taskList: List<Task> = arrayListOf()
    private var v: View? = null
    private var populateTaskJob0: Job? = null
    private var populateTaskJob1: Job? = null
    private var camPermission: Boolean = false
    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_AZTEC
        )
        .build()

    private val REQUEST_IMAGE_CAPTURE = 1

    private var imgBitmap: Bitmap? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch(Dispatchers.Main) {
            populateList()
            // populateListFromLocalDB()

        }
        setTaskAdapter()
        taskSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterTaskList(newText)
                return true
            }

        })

        qrcodeBtn.setOnClickListener(View.OnClickListener {
           if(camPermission){
               scanImg()
           }else{
               requestPermissions()
           }

        })

    }

    private fun filterTaskList(query: String?) {
        var filteredTaskList: ArrayList<Task> = arrayListOf()
        for (i in taskList) {
            if (i.task?.lowercase(java.util.Locale.ROOT)?.contains(query?.lowercase()!!)!! ||
                i.title?.lowercase(java.util.Locale.ROOT)?.contains(query?.lowercase()!!)!! ||
                i.description?.lowercase(java.util.Locale.ROOT)?.contains(query?.lowercase()!!)!! ||
                i.sort?.lowercase(java.util.Locale.ROOT)?.contains(query?.lowercase()!!)!! ||
                i.wageType?.lowercase(java.util.Locale.ROOT)?.contains(query?.lowercase()!!)!! ||
                i.BusinessUnitKey?.let {
                    i.BusinessUnitKey!!.lowercase(java.util.Locale.ROOT)
                        ?.contains(query?.lowercase()!!)!!
                } == true ||
                i.businessUnit?.lowercase(java.util.Locale.ROOT)
                    ?.contains(query?.lowercase()!!)!! ||
                i.parentTaskID?.lowercase(java.util.Locale.ROOT)
                    ?.contains(query?.lowercase()!!)!! ||
                i.preplanningBoardQuickSelect?.let {
                    i.preplanningBoardQuickSelect!!.lowercase(java.util.Locale.ROOT)
                        ?.contains(query?.lowercase()!!)!!
                } == true ||
                i.colorCode?.lowercase(java.util.Locale.ROOT)?.contains(query?.lowercase()!!)!! ||
                i.workingTime?.let {
                    i.workingTime!!.lowercase(java.util.Locale.ROOT)
                        ?.contains(query?.lowercase()!!)!!
                } == true ||
                i.isAvailableInTimeTrackingKioskMode.toString()?.lowercase(java.util.Locale.ROOT)
                    .contains(query?.lowercase()!!)
            ) {
                filteredTaskList.add(i)
            }
        }
        taskAdapter.updateTasks(filteredTaskList)

    }

    private suspend fun populateList() {
        populateTaskJob0?.cancel()
        populateTaskJob0 = lifecycleScope.launch {
            taskListViewModel.getAllLocalTasks().collectLatest {
                taskList = it
            }
        }
        populateTaskJob1 = lifecycleScope.launch {
            if (taskList.isNotEmpty()) {
                taskAdapter.updateTasks(taskList)
            } else {

                taskListViewModel.getAllTasks().collectLatest {
                    taskAdapter.updateTasks(it)
                    taskList = it
                    for (i in it.indices) {
                        taskListViewModel.insertTask(
                            Task(
                                it[i].task!!,
                                it[i].title,
                                it[i].description,
                                it[i].sort,
                                it[i].wageType,
                                it[i].BusinessUnitKey,
                                it[i].businessUnit,
                                it[i].parentTaskID,
                                it[i].preplanningBoardQuickSelect,
                                it[i].colorCode,
                                it[i].workingTime,
                                it[i].isAvailableInTimeTrackingKioskMode
                            )
                        )
                    }
                }
            }
        }
        populateTaskJob0?.join()
        populateTaskJob1?.join()


    }

    private suspend fun populateListFromLocalDB() {
        taskListViewModel.getAllLocalTasks().collectLatest {
            taskList = it
            taskAdapter.updateTasks(it)
        }
    }

    private fun setTaskAdapter() {
        recyclerViewTask.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = taskAdapter
        }
    }


    private fun scanImg() {

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        try {

            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)

        } catch (e: Exception) {

            Toast.makeText(
                activity, e.message,
                Toast.LENGTH_LONG
            ).show();

        }


    }

    private fun processScan() {
        if (imgBitmap != null) {

            val mImg = InputImage.fromBitmap(imgBitmap!!, 0)
            val mScanner = BarcodeScanning.getClient(options)

            mScanner.process(mImg).addOnSuccessListener { qrcodes ->

                if (qrcodes.toString() == "[]") {
                    Toast.makeText(
                        activity, "nothing to scan",
                        Toast.LENGTH_LONG
                    ).show();

                }


                for (qrcode in qrcodes) {
                    val qrcodeType = qrcode.valueType
                    when (qrcodeType) {

                        Barcode.TYPE_TEXT -> {
                            qrcode.rawValue?.let {
                                val text = qrcode.rawValue!!.toString()
                                taskSearchView.setQuery(text, true)
                            }

                        }

                    }
                }


            }
                .addOnFailureListener {

                    Toast.makeText(
                        activity, "QRCode scanning failed",
                        Toast.LENGTH_LONG
                    ).show();

                }


        } else {


            Toast.makeText(
                activity, "No image selected to scan for QR",
                Toast.LENGTH_LONG
            ).show();


        }


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == AppCompatActivity.RESULT_OK) {

            val extras: Bundle? = data?.extras
            imgBitmap = extras?.get("data") as Bitmap
            imgBitmap?.let {
                processScan()
            }

        }


    }

    private fun requestPermissions() {
        if (PermissionUtil.hasLocationPermissions(requireContext())) {
            camPermission = true
            scanImg()

        } else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                EasyPermissions.requestPermissions(
                    this,
                    "you need to accept camera permissions to use this app for QR",
                    0,
                    Manifest.permission.CAMERA,
                )
            } else {
                EasyPermissions.requestPermissions(
                    this,
                    "you need to accept camera permissions to use this app for QR",
                    0,
                    Manifest.permission.CAMERA,

                    )
            }

        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        lifecycleScope.launch {
            camPermission = true
            scanImg()

        }

    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).build().show()
        } else {
            requestPermissions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }


}

