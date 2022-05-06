package com.ollivolland.ffekommando

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class DataBaseWrapper(context: Context) {

    val reader: DataBaseReader
    val writer: DataBaseWriter

    init {
        if (!isInitialized()) {
            val config = FirebaseOptions.Builder()
            config.setApplicationId("1:569380100863:android:2174af7069e7b2a680caca")
            config.setProjectId("569380100863")
            config.setDatabaseUrl("https://ffcommand-default-rtdb.europe-west1.firebasedatabase.app")
            FirebaseApp.initializeApp(context, config.build())

            db = Firebase.database.reference
        }

        reader = DataBaseReader(this)
        writer = DataBaseWriter(this)
    }

    companion object {
        lateinit var db:DatabaseReference
        fun isInitialized() = ::db.isInitialized

        operator fun get(context: Context): Pair<DataBaseReader, DataBaseWriter> {
            val wrapper = DataBaseWrapper(context)
            return Pair(wrapper.reader, wrapper.writer)
        }
    }
}

class DataBaseReader(wrapper: DataBaseWrapper) {

//    operator fun set(path: String, success: (a: DataSnapshot) -> Unit) {
//        DataBaseWrapper.db.child(path).get()
//            .addOnSuccessListener { success(it) }
//    }

    operator fun set(path: String, success: (a: Task<DataSnapshot>) -> Unit) {
        DataBaseWrapper.db.child(path).get()
            .addOnCompleteListener { success(it) }
    }

//    operator fun set(path: String, listeners: Pair<(a: DataSnapshot) -> Unit, (a: Exception) -> Unit>) {
//        DataBaseWrapper.db.child(path).get()
//            .addOnSuccessListener { listeners.first(it) }
//            .addOnFailureListener { listeners.second(it) }
//    }
}

class DataBaseWriter(wrapper: DataBaseWrapper) {

//    fun set2(path: String, actions: Pair<(MutableData) -> MutableData, (Boolean) -> Unit>) {
//        DataBaseWrapper.db.child(path).runTransaction(object : Transaction.Handler {
//            override fun doTransaction(mutableData: MutableData): Transaction.Result {
//                return Transaction.success(actions.first(mutableData))
//            }
//
//            override fun onComplete(databaseError: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
//                actions.second(committed)
//            }
//        })
//    }

//    operator fun set(path: String, action: (MutableData) -> MutableData) {
//        set2(path, Pair(action) {})
//    }

    operator fun set(path:String, map: HashMap<String, Any?>, listener: (Boolean) -> Unit) {
        DataBaseWrapper.db.child(path).updateChildren(map)
            .addOnSuccessListener { listener(true) }
            .addOnFailureListener { listener(false) }
    }

    operator fun set(path:String, map: HashMap<String, Any?>) {
        DataBaseWrapper.db.child(path).updateChildren(map)
    }

//    operator fun set(path:String, value:String) {
//        DataBaseWrapper.db.child(path).setValue(value)
//    }

    operator fun minus(path: String) {
        DataBaseWrapper.db.child(path).removeValue()
    }
}