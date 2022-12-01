package ru.vasic2000.netovoiceassistent

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SimpleAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
//Библиотека вольфрам
import com.wolfram.alpha.WAEngine
import com.wolfram.alpha.WAPlainText
//Корутина. Аналог postOnUIThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

//    My APPID: U9JVQP-HHHY4HXEL6

    val TAG = "MainActivity"
    lateinit var requestText: TextInputEditText
    lateinit var podsAdapter: SimpleAdapter
    lateinit var progressBar: ProgressBar

    lateinit var waEngine: WAEngine

    val pods = mutableListOf<HashMap<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initWolframEngine()
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

        val voiceInputButton : FloatingActionButton = findViewById(R.id.voice_input_button)
        voiceInputButton.setOnClickListener {
            Log.d(TAG, "FAB pressed")
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


            }.onFailure { t ->
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
//             Если месседж не нулл - то месседж, иначе нашу ошибку "Что-то пошло не так"
                    showSnackBar(t.message ?: getString(R.string.error_something_went_wrong))
                }
            }
        }
    }
}