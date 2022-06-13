package com.ollivolland.ffekommando

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class MyDB(val context: Context) {

    init {
        if (!isInitialized()) db = Firebase.database.reference
    }

    operator fun get(path: String, onResult: (Task<DataSnapshot>) -> Unit) {
        db.child(path).get()
            .addOnCompleteListener { onResult(it) }
    }

    fun listen(path: String, onResult: (DataSnapshot) -> Unit) {
        db.child(path).addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    onResult(dataSnapshot)
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    // Getting Post failed, log a message
                    Log.w("TAG", "loadPost:onCancelled", databaseError.toException())
                }
            })
    }

    operator fun set(path:String, value:Any?) {
        db.child(path).setValue(value)
    }

    operator fun set(path:String, map: HashMap<String, Any?>) {
        db.child(path).updateChildren(map)
    }

    operator fun set(path:String, onSuccess: () -> Unit, map: HashMap<String, Any?>) {
        db.child(path).updateChildren(map)
            .addOnSuccessListener { onSuccess() }
    }

    operator fun set(path:String, onComplete: (Task<Void>) -> Unit, map: HashMap<String, Any?>) {
        db.child(path).updateChildren(map)
            .addOnCompleteListener { onComplete(it) }
    }

    operator fun minus(path: String) {
        db.child(path).removeValue()
    }

    companion object {
        private lateinit var db:DatabaseReference
        fun isInitialized() = ::db.isInitialized
    }
}