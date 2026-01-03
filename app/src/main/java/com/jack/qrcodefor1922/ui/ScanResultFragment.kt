package com.jack.qrcodefor1922.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.jack.qrcodefor1922.R
import com.jack.qrcodefor1922.ui.database.ScanResult


/**
 * A fragment representing a list of Items.
 */
class ScanResultFragment : Fragment() {

    private val viewModel: ScanResultViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private var rewardedAd: RewardedAd? = null
    private lateinit var list: RecyclerView
    private lateinit var emptyView: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_item_list, container, false)
        list = view.findViewById(R.id.list)
        emptyView = view.findViewById(R.id.empty_view)

        list.layoutManager = LinearLayoutManager(context)
        list.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))

        // 觀察資料和解鎖狀態
        viewModel.resultData.observe(viewLifecycleOwner) { results ->
            updateAdapter(results, viewModel.isUnlocked.value ?: false)
        }

        viewModel.isUnlocked.observe(viewLifecycleOwner) { isUnlocked ->
            viewModel.resultData.value?.let { results ->
                updateAdapter(results, isUnlocked)
            }
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.getAllResult(view.context.applicationContext)
        loadRewardedAd()
    }

    private fun updateAdapter(results: List<ScanResult>, isUnlocked: Boolean) {
        list.adapter = ScanResultRecyclerViewAdapter(
            results = results,
            isUnlocked = isUnlocked,
            onItemClick = { result ->
                mainViewModel.onHistoryItemClicked(result)
            },
            onViewMoreClick = {
                showRewardedAd()
            }
        )

        if (results.isEmpty()) {
            list.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            list.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        val adUnitId = getString(R.string.admob_unlock_history_id)

        RewardedAd.load(requireContext(), adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, "Ad failed to load: ${adError.message}")
                rewardedAd = null
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Log.d(TAG, "Ad was loaded.")
                rewardedAd = ad
                setupAdCallbacks()
            }
        })
    }

    private fun setupAdCallbacks() {
        rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad was dismissed.")
                rewardedAd = null
                loadRewardedAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.d(TAG, "Ad failed to show: ${adError.message}")
                rewardedAd = null
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad showed fullscreen content.")
            }
        }
    }

    private fun showRewardedAd() {
        val ad = rewardedAd
        if (ad != null) {
            ad.show(requireActivity()) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                viewModel.unlockHistory()
            }
        } else {
            Toast.makeText(requireContext(), "廣告載入中，請稍候再試", Toast.LENGTH_SHORT).show()
            loadRewardedAd()
        }
    }

    companion object {
        private const val TAG = "ScanResultFragment"
    }
}
