package com.example.teste

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.android.volley.Request
import com.android.volley.Response
//import com.devbrackets.android.exomedia.listener.OnPreparedListener
import com.jcraft.jsch.ChannelExec
import kotlinx.android.synthetic.main.activity_main.*
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.ByteArrayOutputStream
import java.util.Properties
import android.net.nsd.NsdServiceInfo

import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.DiscoveryListener
import android.util.Log
import java.net.InetAddress


class MainActivity : AppCompatActivity() { //, OnPreparedListener
    lateinit var conexHandler: Handler

    // Network Service Discovery related members
    // This allows the app to discover the garagedoor.local
    // "service" on the local network.
    // Reference: http://developer.android.com/training/connect-devices-wirelessly/nsd.html
    lateinit var mNsdManager: NsdManager
    var mDiscoveryListener: DiscoveryListener? = null
    var mResolveListener: NsdManager.ResolveListener? = null
    var mServiceInfo: NsdServiceInfo? = null
    var mRPiAddress: String? = null

    // The NSD service type that the RPi exposes.
    val SERVICE_TYPE = "_http._tcp."


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //setupVideoView()
        createNotificationChannel()

        conexHandler = Handler(Looper.getMainLooper())

        mRPiAddress = ""
        var mNsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        //mNsdManager = (NsdManager)(getApplicationContext().getSystemService(Context.NSD_SERVICE))
        initializeResolveListener()
        initializeDiscoveryListener()
        mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }


    private fun initializeDiscoveryListener() {

        // Instantiate a new DiscoveryListener
        mDiscoveryListener = object : DiscoveryListener {
            //  Called as soon as service discovery begins.
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                // A service was found!  Do something with it.
                val name = service.serviceName
                val type = service.serviceType
                Log.d("NSD", "Service Name=$name")
                Log.d("NSD", "Service Type=$type")
                if (type == SERVICE_TYPE && name.contains("lucas-Virtual-Machine")) {
                    Log.d("NSD", "Service Found @ '$name'")
                    mNsdManager?.resolveService(service, mResolveListener)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                mNsdManager?.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                mNsdManager?.stopServiceDiscovery(this)
            }
        }
    }



    private fun initializeResolveListener() {
        mResolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Called when the resolve fails.  Use the error code to debug.
                Log.e("NSD", "Resolve failed$errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                mServiceInfo = serviceInfo

                // Port is being returned as 9. Not needed.
                //int port = mServiceInfo.getPort();
                val host: InetAddress = mServiceInfo!!.getHost()
                val address: String = host.getHostAddress()
                Log.d("NSD", "Resolved address = $address")
                mRPiAddress = address
            }
        }
    }














    fun testeSSH(view: View){
        SshTask().execute()
    }

    class SshTask : AsyncTask<Void, Void, String>() {
        override fun doInBackground(vararg p0: Void?): String {
            val username = "pi"
            val password = "pilinux"
            val hostname = "192.168.1.100"
            /*val command = "python a/grace.py"
            val killCommand = "pkill -15 -f grace.py"*/
            val command = "python servo/movimentodebalanco1.py"
            val killCommand = "pkill -15 -f movimentodebalanco"
            val output2 = enviarSSH(username, password, hostname, killCommand)
            val output = enviarSSH(username, password, hostname, command)
            return output
        }
    }



    fun monitorarConexao(view: View) {
        conexHandler.post(monitorarConexaoTask)
    }


    fun interromperMonitoramento(view: View) {
        conexHandler.removeCallbacks(monitorarConexaoTask)
    }

    private val monitorarConexaoTask = object : Runnable {
        override fun run() {
            serverRequest()
            conexHandler.postDelayed(this, 10000)
        }
    }



    private fun createNotification() {
        // Create an explicit intent for an Activity in your app
        val intent = Intent(this, this::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)

        var builder = NotificationCompat.Builder(this, "channel1")
            .setSmallIcon(R.mipmap.ic_launcher_round)//(R.drawable.notification_icon)
            .setContentTitle("Cadeirinha desconectada")
            .setContentText("Você perdeu a conexão com seu bebê")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Set the intent that will fire when the user taps the notification
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            notify(0, builder.build())
        }
    }



    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("channel1", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }




    fun serverRequest() { //view: View
        val textView = findViewById<TextView>(R.id.textView)
// ...

// Instantiate the RequestQueue.
        val queue = Volley.newRequestQueue(this)
        val url = "http://192.168.25.159:5000"

// Request a string response from the provided URL.
        val stringRequest = StringRequest(Request.Method.GET, url,
            Response.Listener<String> { response ->
                // Display the first 500 characters of the response string.
                textView.text = "${response.substring(0, 10)}"
            },
            Response.ErrorListener { createNotification()//textView.text = "That didn't work!"
            })

// Add the request to the RequestQueue.
        queue.add(stringRequest)
    }


/*    private fun setupVideoView() {
        // Make sure to use the correct VideoView import
        video_view.setOnPreparedListener(this)

        //For now we just picked an arbitrary item to play
        //video_view.setVideoURI(Uri.parse("https://s3.amazonaws.com/shiftone-video-test/video/index.m3u8"))
        video_view.setVideoURI(Uri.parse("https://multiplatform-f.akamaihd.net/i/multi/will/bunny/big_buck_bunny_,640x360_400,640x360_700,640x360_1000,950x540_1500,.f4v.csmil/master.m3u8"))
    }

    override fun onPrepared() {
        video_view.start()
    }*/

}

fun enviarSSH(username: String,
              password: String,
              hostname: String,
              command: String): String {
    val port = 22

    val jsch = JSch()
    val session = jsch.getSession(username, hostname, port)
    session.setPassword(password)

    // Avoid asking for key confirmation.
    val properties = Properties()
    properties.put("StrictHostKeyChecking", "no")
    session.setConfig(properties)

    session.connect()

    // Create SSH Channel.
    val sshChannel = session.openChannel("exec") as ChannelExec
    val outputStream = ByteArrayOutputStream()
    sshChannel.outputStream = outputStream

    // Execute killCommand.
    sshChannel.setCommand(command)
    sshChannel.connect()

    // Sleep needed in order to wait long enough to get result back.
    Thread.sleep(1_000)

    sshChannel.disconnect()

    session.disconnect()

    //return String(outputStream.toByteArray())
    return outputStream.toString()
}