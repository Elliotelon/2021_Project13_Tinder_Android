package fastcampus.aop.part3.chapter05

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.yuyakaido.android.cardstackview.CardStackLayoutManager
import com.yuyakaido.android.cardstackview.CardStackListener
import com.yuyakaido.android.cardstackview.CardStackView
import com.yuyakaido.android.cardstackview.Direction
import fastcampus.aop.part3.chapter05.DBKey.Companion.DIS_LIKE
import fastcampus.aop.part3.chapter05.DBKey.Companion.LIKE
import fastcampus.aop.part3.chapter05.DBKey.Companion.LIKED_BY
import fastcampus.aop.part3.chapter05.DBKey.Companion.NAME
import fastcampus.aop.part3.chapter05.DBKey.Companion.USERS
import fastcampus.aop.part3.chapter05.DBKey.Companion.USER_ID

class LikeActivity : AppCompatActivity(), CardStackListener {

    private lateinit var usersDb: DatabaseReference
    private var auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val adapter = CardItemAdapter()
    private val cardItems = mutableListOf<CardItem>()
    private val manager by lazy {
        CardStackLayoutManager(this, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_like)

        usersDb = FirebaseDatabase.getInstance().reference.child(USERS)

        val currentUserDB = usersDb.child(getCurrentUserID())
        currentUserDB.addListenerForSingleValueEvent(object:ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.child(NAME).value == null) {
                    showNameInputPopup()
                    return
                }
                getUnSelectedUsers()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        initCardStackView()
        initSignOutButton()
        initMatchedListButton()
    }
    private fun initCardStackView() {
        val stackView = findViewById<CardStackView>(R.id.cardStackView)
        stackView.layoutManager = manager
        stackView.adapter = adapter
    }

    private fun initSignOutButton() {
        val signOutButton = findViewById<Button>(R.id.signOutButton)
        signOutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun initMatchedListButton() {
        val matchedListButton = findViewById<Button>(R.id.matchListButton)
        matchedListButton.setOnClickListener {

            startActivity(Intent(this, MatchedUserActivity::class.java))
        }
    }

    private fun getCurrentUserID(): String {
        if (auth.currentUser == null) {
            Toast.makeText(this, "로그인이 되어있지않습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
        return auth.currentUser.uid
    }

    fun getUnSelectedUsers() {
        usersDb.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, s: String?) {
                if (snapshot.child(USER_ID).value != getCurrentUserID()
                    && snapshot.child(LIKED_BY).child(LIKE).hasChild(getCurrentUserID()).not()
                    && snapshot.child(LIKED_BY).child(DIS_LIKE).hasChild(getCurrentUserID()).not()) {

                    val userId = snapshot.child(USER_ID).value.toString()
                    var name = "undecided"
                    if (snapshot.child(NAME).value != null) {
                        name = snapshot.child(NAME).value.toString()
                    }

                    cardItems.add(CardItem(userId, name))
                    adapter.submitList(cardItems)
                    adapter.notifyDataSetChanged()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, s: String?) {
                cardItems.find {it.userId == snapshot.key}?.let {
                    it.name = snapshot.child(NAME).value.toString()
                }
                adapter.submitList(cardItems)
                adapter.notifyDataSetChanged()

            }
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, s: String?) {}
            override fun onCancelled(snapshot: DatabaseError) {}
        })
    }

    private fun showNameInputPopup() {
        val editText = EditText(this)
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.write_name)
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                if (editText.text.isEmpty()) {
                    showNameInputPopup()
                } else {
                    saveUserName(editText.text.toString())
                }

            }
            .setCancelable(false)
            .show()

    }

    private fun saveUserName(name: String) {
        val userId: String = getCurrentUserID()
        val currentUserDb = usersDb.child(userId)
        val user = mutableMapOf<String, Any>()
        user["userId"] = userId
        user["name"] = name
        currentUserDb.updateChildren(user)

        getUnSelectedUsers()
    }

    @ExperimentalStdlibApi
    private fun like() {
        val card = cardItems[manager.topPosition - 1]
        cardItems.removeFirst()

        usersDb.child(card.userId)
            .child("likedBy")
            .child("like")
            .child(getCurrentUserID())
            .setValue(true)

        saveMatchIfOtherUserLikedMe(card.userId)
        //매칭이 된 시점

        Toast.makeText(this, "${card.name}님을 Like 하셨습니다.", Toast.LENGTH_SHORT ).show()
    }

    @ExperimentalStdlibApi
    private fun disLike() {
        val card = cardItems[manager.topPosition - 1]
        cardItems.removeFirst()

        usersDb.child(card.userId)
            .child("likedBy")
            .child("disLike")
            .child(getCurrentUserID())
            .setValue(true)

        Toast.makeText(this, "${card.name}님을 disLike 하셨습니다.", Toast.LENGTH_SHORT ).show()
    }

    private fun saveMatchIfOtherUserLikedMe(otherUserId: String) {
        val otherUserDB = usersDb.child(getCurrentUserID()).child("likedBy").child("like").child(otherUserId)
        otherUserDB.addListenerForSingleValueEvent(object : ValueEventListener{

            override fun onDataChange(snapshot: DataSnapshot) {
                if(snapshot.value == true) {
                    usersDb.child(getCurrentUserID())
                        .child("likedBy")
                        .child("match")
                        .child(otherUserId)
                        .setValue(true)

                    usersDb.child(otherUserId)
                        .child("likedBy")
                        .child("match")
                        .child(getCurrentUserID())
                        .setValue(true)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }



    override fun onCardDragging(direction: Direction?, ratio: Float) {

    }

    @ExperimentalStdlibApi
    override fun onCardSwiped(direction: Direction?) {
        when(direction) {
            Direction.Right -> like()
            Direction.Left -> disLike()
            else -> {

            }
        }
    }

    override fun onCardRewound() {}

    override fun onCardCanceled() {}

    override fun onCardAppeared(view: View?, position: Int) {}

    override fun onCardDisappeared(view: View?, position: Int) {}


}