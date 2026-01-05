package com.dchat.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dchat.core.network.DhtNetworkManager
import com.dchat.core.security.CryptoEngine
import com.dchat.data.local.DatabaseBuilder
import com.dchat.domain.ContactDiscoveryService
import kotlinx.coroutines.launch
import android.app.Application

class ContactsViewModel(application: Application) : ViewModel() {

    private val dhtManager = DhtNetworkManager()
    
    // Получаем ключ БД из Keystore (упрощено)
    private val dbKey = "local_secret_key".toByteArray() 
    private val db = DatabaseBuilder.getInstance(application, dbKey)
    
    private val discoveryService = ContactDiscoveryService(dhtManager, db.contactDao())

    fun onSyncContactsClicked(deviceContacts: List<Pair<String, String>>) {
        viewModelScope.launch {
            // Запускаем процесс поиска, описанный в ТЗ
            discoveryService.syncContacts(deviceContacts)
        }
    }
    
    fun onFirstLaunch(myPhone: String) {
        viewModelScope.launch {
            val keys = CryptoEngine.generateNewKeys()
            // Сохраняем ключи secure storage...
            
            // Публикуем себя
            val pubKeyJson = keys.publicKeysetHandle.toString()
            val myId = CryptoEngine.getUserId(keys)
            
            discoveryService.publishMyIdentity(myPhone, pubKeyJson, myId)
        }
    }
}
