package com.verodigital.androidtask.domain

import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.verodigital.androidtask.data.datasource.getAllTasksResponse
import com.verodigital.androidtask.data.datasource.local.Task
import com.verodigital.androidtask.data.repository.LoginRepository
import com.verodigital.androidtask.data.repository.TasksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val mTasksRepository: TasksRepository,
    private val mLoginRepository: LoginRepository
): ViewModel() {
    suspend fun getAllTasks(): Flow<List<Task>> {
        return mTasksRepository.getAllTasks(mLoginRepository.getAccessToken())
    }
}