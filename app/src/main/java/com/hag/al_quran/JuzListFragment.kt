package com.hag.al_quran

import Juz
import androidx.fragment.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class JuzListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: JuzAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_juz_list, container, false)
        recyclerView = view.findViewById(R.id.juzRecyclerView)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // بيانات الأجزاء
        val juzList = listOf(
            Juz(1, "الجزء الأول", 1),
            Juz(2, "الجزء الثاني", 22),
            Juz(3, "الجزء الثالث", 42)
            // ... أكمل باقي الأجزاء
        )
        adapter = JuzAdapter(juzList) { juz ->
            // عند الضغط على جزء: مثال عرض Toast
            // Toast.makeText(requireContext(), juz.name, Toast.LENGTH_SHORT).show()
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }
}
