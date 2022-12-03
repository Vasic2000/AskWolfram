package ru.vasic2000.netovoiceassistent

//Библиотека вольфрам
//Корутина. Аналог postOnUIThread
import android.R.attr
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SimpleAdapter
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.wolfram.alpha.WAEngine
import com.wolfram.alpha.WAPlainText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*


class MainActivity : AppCompatActivity() {

//    My APPID: U9JVQP-HHHY4HXEL6

    val TAG = "MainActivity"
    lateinit var requestText: TextInputEditText
    lateinit var podsAdapter: SimpleAdapter
    lateinit var progressBar: ProgressBar

    lateinit var waEngine: WAEngine

    val pods = mutableListOf<HashMap<String, String>>()

    lateinit var textToSpeech: TextToSpeech
    var isTtsReady: Boolean = false
    val VOICE_REQUEST_CODE: Int = 797

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initWolframEngine()
        initTTS()
    }

    private fun initTTS() {
        textToSpeech = TextToSpeech(this) {code ->
            if(code != TextToSpeech.SUCCESS) {
                Log.e(TAG, "TTS error code: $code")
                showSnackBar(getString(R.string.error_tts_isnt_ready))
            }
            isTtsReady = true
            textToSpeech.language = Locale.US
        }
    }

    private fun initViews() {
        val m_toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(m_toolbar)

        requestText = findViewById(R.id.text_input_edit)
//      Обраьотка нажатия Ввода в поле requestInputText
        requestText.setOnEditorActionListener { textView, i, keyEvent ->
            if(i == EditorInfo.IME_ACTION_DONE) {
                //  Выкинуть данные из списка
                pods.clear()
                //  Сообщить адаптеру, что он изменён
                podsAdapter.notifyDataSetChanged()
                //  Получить вопрос
                val question = requestText.text.toString()
                //  Спросить Вольфрам
                askWolFrame(question)
            }
//          Спрятать клавиатуру после ввода текста
            return@setOnEditorActionListener false
        }


        val podsList: ListView = findViewById(R.id.pods_list)

        podsAdapter = SimpleAdapter(
            applicationContext,
            pods,
            R.layout.item_pod,
            arrayOf("Title", "Content"),
            intArrayOf(R.id.title_text, R.id.text_content)
        )

        podsList.adapter = podsAdapter

        podsList.setOnItemClickListener {parent, view, position, id ->
            if(isTtsReady) {
                val title = pods[position]["Title"]
                val content = pods[position]["Content"]
                textToSpeech.speak(content, TextToSpeech.QUEUE_FLUSH, null, title)
            }
        }

        val voiceInputButton : FloatingActionButton = findViewById(R.id.voice_input_button)
        voiceInputButton.setOnClickListener {
            Log.d(TAG, "FAB pressed")

            pods.clear()
            podsAdapter.notifyDataSetChanged()
            if(isTtsReady) {
                textToSpeech.stop()
            }
            showInpuDialog()
        }

        progressBar = findViewById(R.id.progressBar)

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.action_clear -> {
                Log.d(TAG, "action_clear")

                // Почистить текстовое поле ввода
                requestText.text?.clear()
                // Почистить список
                pods.clear()
                // Сообщить адаптеру, что изменён список
                podsAdapter.notifyDataSetChanged()

                return true
            }
            R.id.action_stop -> {
                if(isTtsReady) {
                    textToSpeech.stop()
                }
                Log.d(TAG, "action_stop")
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun initWolframEngine() {
        waEngine = WAEngine()
        waEngine.appID = "U9JVQP-HHHY4HXEL6"
        waEngine.addFormat("plaintext")
    }

    fun showSnackBar(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_INDEFINITE).apply {
            setAction(android.R.string.ok) {
                dismiss()
            }
            show()
        }
    }

    fun askWolFrame(request: String) {
        progressBar.visibility = View.VISIBLE

//             Сама корутина
        CoroutineScope(Dispatchers.IO).launch() {
//             Запрос
            val querry = waEngine.createQuery().apply {
                input = request
            }
//             Котлин обработка исключений Tray -> Catch
            kotlin.runCatching {
                waEngine.performQuery(querry)
            }.onSuccess {result ->
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
//                    Когда результат = ошибка
                    if(result.isError) {
                        showSnackBar(result.errorMessage)
                        return@withContext
                    }
//                    Когда ошибка была в запросе
                    if(!result.isSuccess) {
                        requestText.error = getString(R.string.error_dont_understand)
                        return@withContext
                    }

//                Если всё сошлось - собираю ответы

                for(pod in result.pods) {
                    // Ошибка - следующий элемент
                    if(pod.isError) continue

                    val content = java.lang.StringBuilder()
                    for(subpod in pod.subpods) {
                        for(element in subpod.contents) {
                            if(element is WAPlainText) {
                                content.append(element.text)
                            }
                        }
                    }
                    pods.add(0, HashMap<String, String>().apply() {
                        put("Title", pod.title)
                        put("Content", content.toString())
                    })

                }
//                Сообщить podsAdapter'у, что данные обновлены
                podsAdapter.notifyDataSetChanged()
                }
            }.onFailure { t ->
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
//             Если месседж не нулл - то месседж, иначе нашу ошибку "Что-то пошло не так"
                    showSnackBar(t.message ?: getString(R.string.error_something_went_wrong))
                }
            }
        }
    }

    fun showInpuDialog() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.request_hint))
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
        }

        kotlin.runCatching {
            startActivityForResult(intent, VOICE_REQUEST_CODE)
        }.onFailure {t ->
            showSnackBar(t.message ?: getString(R.string.error_voice_recognition_unavailable))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == VOICE_REQUEST_CODE && resultCode == RESULT_OK) {
            val matches: ArrayList<String>? = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if(matches?.isEmpty() != true) {
                requestText.setText(matches?.get(0))
                matches?.get(0)?.let { askWolFrame(it) }
            }

//            Log.e(TAG, data?.getStringArrayExtra(RecognizerIntent.EXTRA_RESULTS)?.first().toString())
//            Log.d(TAG, data?.getStringArrayExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0).toString())
//            data?.getStringArrayExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)?.let {question ->
//                requestText.setText(question)
//                askWolFrame(question)
//            }
        }
    }
}