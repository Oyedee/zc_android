package com.tolstoy.zurichat.ui.fragments.channel_chat

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.format.DateUtils
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.tolstoy.zurichat.R
import com.tolstoy.zurichat.databinding.FragmentChannelChatBinding
import com.tolstoy.zurichat.models.ChannelModel
import com.tolstoy.zurichat.models.User
import com.tolstoy.zurichat.ui.add_channel.BaseItem
import com.tolstoy.zurichat.ui.add_channel.BaseListAdapter
import com.tolstoy.zurichat.ui.channel_info.ChannelInfoActivity
import com.tolstoy.zurichat.ui.fragments.model.Data
import com.tolstoy.zurichat.ui.fragments.model.JoinChannelUser
import com.tolstoy.zurichat.ui.fragments.model.Message
import com.tolstoy.zurichat.ui.fragments.viewmodel.ChannelMessagesViewModel
import com.tolstoy.zurichat.ui.fragments.viewmodel.ChannelViewModel
import dev.ronnie.github.imagepicker.ImagePicker
import dev.ronnie.github.imagepicker.ImageResult
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.random.Random
import centrifuge.Centrifuge
import centrifuge.Client
import com.tolstoy.zurichat.ui.fragments.networking.AppDisconnectHandler

import centrifuge.DisconnectHandler

import com.tolstoy.zurichat.ui.fragments.networking.AppConnectHandler

import centrifuge.ConnectHandler
import kotlinx.coroutines.*
import okhttp3.Dispatcher
import java.lang.Exception


class ChannelChatFragment : Fragment() {
    private val viewModel: ChannelViewModel by viewModels()
    private lateinit var binding: FragmentChannelChatBinding
    private lateinit var user: User
    private lateinit var channel: ChannelModel
    private lateinit var organizationID: String
    private lateinit var roomData: RoomData
    private var channelJoined = false
    private var members: ArrayList<User>? = null
    private var isEnterSend: Boolean = false

    private val channelMsgViewModel: ChannelMessagesViewModel by viewModels()
    private lateinit var channelListAdapter: BaseListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentChannelChatBinding.inflate(inflater, container, false)
        val bundle = arguments
        if (bundle != null) {
            members = bundle.getParcelableArrayList("selected_members")!!
            user = bundle.getParcelable("USER")!!
            channel = bundle.getParcelable("Channel")!!
            channelJoined = bundle.getBoolean("Channel Joined")
            organizationID = "614679ee1a5607b13c00bcb7"
        }

        isEnterSend = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getBoolean("enter_to_send", false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // code to control the dimming of background
        val prefMngr = PreferenceManager.getDefaultSharedPreferences(context)
        val dimVal = prefMngr.getInt("bar", 50).toFloat().div(100f)

        val dimmerBox = binding.dmChatDimmer
        val channelChatEdit =
            binding.channelChatEditText           //get message from this edit text
        val sendVoiceNote = binding.sendVoiceBtn
        val sendMessage =
            binding.sendMessageBtn                    //use this button to send the message
        val typingBar = binding.channelTypingBar
        val toolbar = view.findViewById<Toolbar>(R.id.channel_toolbar)

        val imagePicker = ImagePicker(this)

        //val includeAttach = binding.attachment
        val attachment = binding.channelLink
        val popupView: View = layoutInflater.inflate(R.layout.partial_attachment_popup, null)
        val popupWindow = PopupWindow(popupView,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT)

        dimmerBox.alpha = dimVal

        if (channelJoined) {
            dimmerBox.visibility = View.GONE
            binding.channelJoinBar.visibility = View.GONE
        } else {
            dimmerBox.visibility = View.VISIBLE
            binding.channelName.text = channel.name

            if (channel.isPrivate) {
                binding.channelName.setCompoundDrawablesRelativeWithIntrinsicBounds(ContextCompat.getDrawable(
                    requireActivity(),
                    R.drawable.ic_new_lock), null, null, null)
            } else {
                binding.channelName.setCompoundDrawablesRelativeWithIntrinsicBounds(ContextCompat.getDrawable(
                    requireActivity(),
                    R.drawable.ic_hash), null, null, null)
            }

            binding.channelJoinBar.visibility = View.VISIBLE

            binding.joinChannel.setOnClickListener {
                binding.joinChannel.visibility = View.GONE
                binding.text2.visibility = View.GONE
                binding.channelName.visibility = View.GONE
                binding.progressBar2.visibility = View.VISIBLE
                user?.let { JoinChannelUser(it.id, "manager") }
                    ?.let { viewModel.joinChannel("1", channel._id, it) }
            }

            viewModel.joinedUser.observe(viewLifecycleOwner, { joinedUser ->
                if (joinedUser != null) {
                    dimmerBox.visibility = View.GONE
                    toolbar.subtitle = channel.members.plus(1).toString().plus(" Members")
                    Toast.makeText(requireContext(),
                        "Joined Channel Successfully",
                        Toast.LENGTH_SHORT).show()
                    binding.channelJoinBar.visibility = View.GONE
                } else {
                    binding.joinChannel.visibility = View.VISIBLE
                    binding.text2.visibility = View.VISIBLE
                    binding.channelName.visibility = View.VISIBLE
                    binding.progressBar2.visibility = View.GONE
                    Toast.makeText(requireContext(),
                        getString(R.string.an_error_occured),
                        Toast.LENGTH_SHORT).show()
                }
            })
        }

        toolbar.title = channel.name
        if (channel.members > 1) {
            toolbar.subtitle = channel.members.toString().plus(" Members")
        } else {
            toolbar.subtitle = channel.members.toString().plus(" Member")
        }
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
        toolbar.setOnClickListener {

            val intent = Intent(requireActivity(), ChannelInfoActivity::class.java)
            intent.putExtra("channel_name", channel.name)
            intent.putExtra("number_of_document", 0)
            intent.putParcelableArrayListExtra("members", members)
            startActivity(intent)
        }

        channelChatEdit.doOnTextChanged { text, start, before, count ->
            if (text.isNullOrEmpty()) {
                sendMessage.isEnabled = false
                sendVoiceNote.isEnabled = true
            } else {
                sendMessage.isEnabled = true
                sendVoiceNote.isEnabled = false
            }
        }

        //Launch Attachment Popup
        popupWindow.setBackgroundDrawable(ColorDrawable())
        popupWindow.isOutsideTouchable = true

        attachment.setOnClickListener {
            //popupWindow.showAtLocation(popupView, Gravity.CENTER, 0, 600)
            popupWindow.showAsDropDown(typingBar, 0, -(typingBar.height * 4), Gravity.TOP)
        }

        setupKeyboard()

        channelListAdapter = BaseListAdapter { channelItem ->

        }
        binding.recyclerMessagesList.adapter = channelListAdapter
        binding.recyclerMessagesList.itemAnimator = null

        binding.cameraChannelBtn.setOnClickListener {
            imagePicker.pickFromStorage { imageResult ->
                when (imageResult) {
                    is ImageResult.Success -> {
                        /*val uri = imageResult.value
                       */
                    }
                    is ImageResult.Failure -> {
                        val errorString = imageResult.errorString
                        Toast.makeText(requireContext(), errorString, Toast.LENGTH_LONG).show()
                    }
                }

            }

        }

        /**
         * Retrieves the channel Id from the channelModel class to get all messages from the endpoint
         * Makes the network call from the ChannelMessagesViewModel
         */
        if (channelMsgViewModel.allMessages.value == null) {
            channelMsgViewModel.retrieveAllMessages(organizationID, channel._id)
        }

        if (channelMsgViewModel.roomData.value == null){
            channelMsgViewModel.retrieveRoomData(organizationID,channel._id)
        }

        // Observes result from the viewModel to be passed to an adapter to display the messages
        channelMsgViewModel.allMessages.observe(viewLifecycleOwner, {
            if (it != null) {
                messagesArrayList.clear()
                messagesArrayList.addAll(it.data)
                val channelsWithDateHeaders = createMessagesList(messagesArrayList)
                channelListAdapter.submitList(channelsWithDateHeaders)

                //Waiting For Adapter To Update Before Scrolling To End Of Message
                //TODO: Look For A Better Way To Do This
                lifecycleScope.launch {
                    delay(100)
                    binding.recyclerMessagesList.scrollToPosition(channelsWithDateHeaders.size-1)
                if (!messagesArrayList.containsAll(it.data)) {
                    messagesArrayList.clear()
                    messagesArrayList.addAll(it.data)
                    val channelsWithDateHeaders = createMessagesList(messagesArrayList)
                    channelListAdapter.submitList(channelsWithDateHeaders)
                    binding.recyclerMessagesList.scrollToPosition(channelsWithDateHeaders.size - 1)
                }
            }
        })

        channelMsgViewModel.roomData.observe(viewLifecycleOwner,{
            roomData = it
            connectToSocket()
        })

        sendMessage.setOnClickListener{
            if (channelChatEdit.text.toString().isNotEmpty()){
        sendMessage.setOnClickListener {
            if (channelChatEdit.text.toString().isNotEmpty()) {
                val s = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                s.timeZone = TimeZone.getTimeZone("UTC")
                val time = s.format(Date(System.currentTimeMillis()))
                val data = Data(generateID().toString(),
                    false,
                    channel._id,
                    channelChatEdit.text.toString(),
                    false,
                    null,
                    null,
                    null,
                    false,
                    false,
                    0,
                    time,
                    "",
                    user.id)
                messagesArrayList.add(data)
                channelMsgViewModel.sendMessages(data, "1", channel._id, messagesArrayList)
                val channelsWithDateHeaders = createMessagesList(messagesArrayList)
                channelListAdapter.submitList(channelsWithDateHeaders)
                binding.recyclerMessagesList.scrollToPosition(channelsWithDateHeaders.size - 1)
            }
        }

        /**
         * This is a bad idea but is crucial to making Sunday demo work.
         * But Centrifugo Channel Subscription isn't working yet so i am stuck with this.
         */
        //Todo: Remove This After Centrifugo RealTime is working
        val handler = Handler(Looper.getMainLooper())
        val runnable = object:Runnable{
            override fun run() {
                channelMsgViewModel.retrieveAllMessages(organizationID, channel._id)
                handler.postDelayed(this,2000)
            }
        }
        handler.postDelayed(runnable,5000)
    }

    private fun connectToSocket(){
        val job = Job()
        val uiScope = CoroutineScope(Dispatchers.Main + job)

        val connectHandler: ConnectHandler = AppConnectHandler(requireActivity(),roomData)
        val disconnectHandler: DisconnectHandler = AppDisconnectHandler(requireActivity())

        val client: Client = Centrifuge.new_("wss://realtime.zuri.chat/connection/websocket", Centrifuge.defaultConfig())
        client.onConnect(connectHandler)
        client.onDisconnect(disconnectHandler)

        uiScope.launch(Dispatchers.IO){
            try {
                client.connect()
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main){
                    Toast.makeText(requireContext(),"Connection Failed",Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupKeyboard() {
        // set keyboard to send if "enter is send" is set to true in settings
        binding.channelChatEditText.apply {
            if (isEnterSend) {
                this.inputType = InputType.TYPE_CLASS_TEXT
                this.imeOptions = EditorInfo.IME_ACTION_SEND
            }
        }

        binding.channelChatEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                // send message

                true
            } else {
                false
            }
        }
    }

    private fun generateID(): Int {
        return Random(6000000).nextInt()
    }

    private var messagesArrayList: ArrayList<Data> = ArrayList()
    private fun createMessagesList(channels: List<Data>): MutableList<BaseItem<*>> {
        // Wrap data in list items
        val channelsItems = channels.map {
            ChannelListItem(it, user, requireActivity())
        }

        val channelsWithDateHeaders = mutableListOf<BaseItem<*>>()
        // Loop through the channels list and add headers where we need them
        var currentHeader: String? = null

        channelsItems.forEach { c ->
            val dateString =
                DateUtils.getRelativeTimeSpanString(convertStringDateToLong(c.data.timestamp.toString()),
                    Calendar.getInstance().timeInMillis,
                    DateUtils.DAY_IN_MILLIS)
            dateString.toString().let {
                if (it != currentHeader) {
                    channelsWithDateHeaders.add(ChannelHeaderItem(it))
                    currentHeader = it
                }
            }
            channelsWithDateHeaders.add(c)
        }

        return channelsWithDateHeaders
    }

    private fun convertStringDateToLong(date: String): Long {
        val s = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        s.timeZone = TimeZone.getTimeZone("UTC")
        var d = s.parse(date)
        return d.time
    }

}