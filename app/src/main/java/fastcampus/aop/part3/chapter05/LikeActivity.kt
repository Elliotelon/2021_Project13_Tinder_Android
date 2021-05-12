package fastcampus.aop.part3.chapter05

import android.os.Bundle
import android.view.View
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

        usersDb = FirebaseDatabase.getInstance().reference.child("Users")

        val currentUserDB = usersDb.child(getCurrentUserID())
        currentUserDB.addListenerForSingleValueEvent(object:ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.child("name").value == null) {
                    showNameInputPopup()
                    return
                }
                getUnSelectedUsers()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        initCardStackView()
    }
    private fun initCardStackView() {
        val stackView = findViewById<CardStackView>(R.id.cardStackView)
        stackView.layoutManager = manager
        stackView.adapter = adapter
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
                if (snapshot.child("userId").value != getCurrentUserID()
                    && snapshot.child("likedBy").child("like").hasChild(getCurrentUserID()).not()
                    && snapshot.child("likedBy").child("disLike").hasChild(getCurrentUserID()).not()) {

                    val userId = snapshot.child("userId").value.toString()
                    var name = "undecided"
                    if (snapshot.child("name").value != null) {
                        name = snapshot.child("name").value.toString()
                    }

                    cardItems.add(CardItem(userId, name))
                    adapter.submitList(cardItems)
                    adapter.notifyDataSetChanged()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, s: String?) {
                cardItems.find {it.userId == snapshot.key}?.let {
                    it.name = snapshot.child("name").value.toString()
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
        builder.setTitle("이름을 입력해주세요")
            .setView(editText)
            .setPositiveButton("확인") { _, _ ->
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