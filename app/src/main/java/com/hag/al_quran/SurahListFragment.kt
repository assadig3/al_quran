import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hag.al_quran.R
import com.hag.al_quran.SurahAdapter

class SurahListFragment : Fragment() {

    private lateinit var surahRecyclerView: RecyclerView
    private lateinit var adapter: SurahAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_surah_list, container, false)
        surahRecyclerView = view.findViewById(R.id.surahRecyclerView)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val surahList = listOf(
            Surah(1, "الفاتحة", "مكية", 7),
            Surah(2, "البقرة", "مدنية", 286),
            Surah(3, "آل عمران", "مدنية", 200)
            // أكمل بقية السور
        )
        adapter = SurahAdapter(surahList) {
            // الإجراء عند الضغط: مثال عرض Toast أو الانتقال لصفحة أخرى
            Toast.makeText(requireContext(), it.name, Toast.LENGTH_SHORT).show()
            // أو افتح صفحة المصحف، أو تفاصيل السورة...
        }
        surahRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        surahRecyclerView.adapter = adapter
    }
}
