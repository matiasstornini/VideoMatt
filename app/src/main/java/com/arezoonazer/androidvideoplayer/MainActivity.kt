package com.arezoonazer.androidvideoplayer

import android.content.ContentValues.TAG
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.arezoonazer.androidvideoplayer.databinding.ActivityMainBinding
import com.arezoonazer.player.argument.PlayerParams
import com.arezoonazer.player.argument.VideoSubtitle
import com.arezoonazer.player.extension.startPlayer
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val binding: ActivityMainBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private var mInterstitialAd: InterstitialAd? = null
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable = Runnable {} // Inicializar con una tarea vacía

    private lateinit var firebaseRemoteConfig: FirebaseRemoteConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        //loadInterstitialAd()
        // Inicializar Firebase Remote Config
        firebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

        // Configura los valores predeterminados de Remote Config
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 0 // Intervalo de actualización de 1 hora
        }
        firebaseRemoteConfig.setConfigSettingsAsync(configSettings)
        firebaseRemoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)

        // Verificar la versión de la app
        checkAppVersion()

        if (intent.hasExtra("m3u8_url")) {
            val videoUrl = intent.getStringExtra("m3u8_url")
            Log.d("MainActivity", "Received video URL from calendar: $videoUrl")
            if (!videoUrl.isNullOrEmpty()) {
                binding.videoUrlEditText.setText(videoUrl)
                startPlayer(getPlayerParam(videoUrl))
                finish() // Cerrar la actividad actual para evitar que el enlace sea visible al retroceder
            }
        }

        with(binding) {
            playButton.setOnClickListener { startPlayer(getPlayerParam()) }

            videoUrlEditText.doOnTextChanged { text, _, _, _ ->
                customPlayButton.isEnabled = text.isNullOrEmpty().not()
            }

            customPlayButton.setOnClickListener { onCustomPlayButtonClicked() }
        }

        // Configurar el Runnable para mostrar el anuncio cada minuto
        /*
        runnable = Runnable {
            showInterstitialAd()
            handler.postDelayed(runnable, 60000) // Reprogramar para cada 60 segundos
        }
        handler.postDelayed(runnable, 60000) // Iniciar la primera ejecución después de 60 segundos

         */
    }

    private fun checkAppVersion() {
        firebaseRemoteConfig.fetchAndActivate()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d(TAG, "Config params updated: $updated")
                    val minimumVersion = firebaseRemoteConfig.getLong("minimum_version")
                    Log.d(TAG, "Minimum version from Firebase Remote Config: $minimumVersion")
                    if (BuildConfig.VERSION_CODE < minimumVersion) {
                        showUpdateDialog()
                    } else {
                        loadInterstitialAd() // Cargar anuncios si la versión es compatible
                    }
                } else {
                    Log.e(TAG, "Error fetching config")
                }
            }
    }


    private fun showUpdateDialog() {
        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle("Update Required")
        alertDialogBuilder.setMessage("Please update the app to continue using it.")
        alertDialogBuilder.setPositiveButton("Update") { dialog, _ ->
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}")

            startActivity(intent)
            finish() // Cerrar la aplicación
        }
        alertDialogBuilder.setCancelable(false)
        alertDialogBuilder.create().show()
    }


    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(this,"ca-app-pub-3940256099942544/1033173712", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, "Ad no was loaded.")
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d(TAG, "Ad was loaded.")
                mInterstitialAd = interstitialAd
                //mInterstitialAd?.show(this@MainActivity) // Muestra el anuncio intersticial cuando está cargado
            }
        })
    }

    private fun showInterstitialAd() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.show(this)
            loadInterstitialAd() // Cargar el próximo anuncio
        } else {
            Log.d(TAG, "El anuncio no está listo todavía.")
            loadInterstitialAd() // Intentar cargar el anuncio nuevamente
        }
    }

    private fun getPlayerParam(customUrl: String? = null): PlayerParams {
        val subtitleList = mutableListOf<VideoSubtitle>().apply {
            add(
                VideoSubtitle(
                    title = "English",
                    url = "https://amara.org/en/subtitles/sbpf8fMnSckL/en/7/download/Big%20Buck%20Bunny.en.vtt"
                )
            )
        }

        val url = customUrl ?: "https://5b44cf20b0388.streamlock.net:8443/vod/smil:bbb.smil/playlist.m3u8"

        return PlayerParams(
            url = url,
            subtitles = subtitleList
        )
    }

    private fun onCustomPlayButtonClicked() {
        with(binding) {
            val videoUrl = videoUrlEditText.text.toString()

            if (videoUrl.isEmpty()) {
                showToast("Please insert video url")
                return
            }

            val subtitleUrl1 = subtitle1UrlEditText.text.toString()
            val subtitleUrl2 = subtitle2UrlEditText.text.toString()

            val subtitleList = mutableListOf<VideoSubtitle>().apply {
                if (subtitleUrl1.isNotEmpty()) {
                    add(
                        VideoSubtitle(
                            title = "subtitle 1",
                            url = subtitleUrl1
                        )
                    )
                }

                if (subtitleUrl2.isNotEmpty()) {
                    add(
                        VideoSubtitle(
                            title = "subtitle 2",
                            url = subtitleUrl2
                        )
                    )
                }
            }

            startPlayer(PlayerParams(videoUrl, subtitleList))
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable) // Detener el Runnable cuando la actividad se destruye
    }
}
