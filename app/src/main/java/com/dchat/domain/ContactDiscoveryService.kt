package com.dchat.domain

import android.util.Log
import com.dchat.core.network.DhtNetworkManager
import com.dchat.core.security.CryptoEngine
import com.dchat.data.local.ContactDao
import com.dchat.data.local.ContactEntity

class ContactDiscoveryService(
    private val dhtManager: DhtNetworkManager,
    private val localDb: ContactDao
) {

    /**
     * Основной метод синхронизации контактов.
     * Выполняет пункты 2 и 3 из описания "Как пользователи находят друг друга".
     */
    suspend fun syncContacts(phoneBook: List<Pair<String, String>>) {
        Log.d("DChat", "Начало синхронизации ${phoneBook.size} контактов")

        phoneBook.forEach { (name, rawPhone) ->
            // Шаг 2. Нормализация и Хеширование
            val phoneHash = CryptoEngine.normalizeAndHashPhone(rawPhone)

            // Шаг 3. Локальная проверка (защита от лишних запросов в сеть)
            val existing = localDb.getContactByHash(phoneHash)
            if (existing != null) {
                // Контакт уже есть, пропускаем
                return@forEach
            }

            // Шаг 4. Поиск в сети (DHT)
            // Реализуем Rate Limit здесь, чтобы не спамить сеть (Пункт 5 "Защита")
            try {
                val dhtResult = dhtManager.lookupIdentity(phoneHash)

                // Шаг 5. Совпадение
                if (dhtResult != null) {
                    // НАЙДЕН!
                    // Сохраняем в локальную зашифрованную БД
                    val newContact = ContactEntity(
                        phoneHash = phoneHash,
                        phoneNumberRaw = rawPhone,
                        name = name,
                        userId = dhtResult.userId,
                        publicKey = dhtResult.publicKey,
                        trustLevel = 1
                    )
                    localDb.insertContact(newContact)
                    Log.i("DChat", "Найден пользователь в P2P сети: $name")
                }
            } catch (e: Exception) {
                Log.e("DChat", "Ошибка поиска хеша $phoneHash", e)
            }
        }
    }
    
    /**
     * Публикация себя в сеть (при первом запуске)
     */
    suspend fun publishMyIdentity(myPhone: String, myKeysJson: String, myId: String) {
        val myHash = CryptoEngine.normalizeAndHashPhone(myPhone)
        
        // Создаем запись
        val record = com.dchat.core.network.DhtRecord(
            userId = myId,
            publicKey = myKeysJson,
            signature = "todo_sign_with_private_key"
        )
        
        // Публикуем в DHT
        dhtManager.announceIdentity(myHash, record)
    }
}
