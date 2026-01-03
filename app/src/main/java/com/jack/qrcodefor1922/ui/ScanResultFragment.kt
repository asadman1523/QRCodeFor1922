package com.jack.qrcodefor1922.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jack.qrcodefor1922.R


/**
 * A fragment representing a list of Items.
 */
class ScanResultFragment : Fragment() {

    private var columnCount = 1
    private val viewModel: ScanResultViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_item_list, container, false)
        val list = view.findViewById<RecyclerView>(R.id.list)
        val emptyView = view.findViewById<View>(R.id.empty_view)

        // Set the adapter
        with(list) {
            layoutManager = when {
                columnCount <= 1 -> LinearLayoutManager(context)
                else -> GridLayoutManager(context, columnCount)
            }
            viewModel.resultData.observe(viewLifecycleOwner) {
                this.adapter = ScanResultRecyclerViewAdapter(it)
                if (it.isEmpty()) {
                    visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                } else {
                    visibility = View.VISIBLE
                    emptyView.visibility = View.GONE
                }
            }
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.getAllResult(view.context.applicationContext)
    }
}