package com.kevin.coupy.ui.screen.donate

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.kevin.coupy.data.donation.DonationEvent
import com.kevin.coupy.data.donation.DonationProduct
import com.kevin.coupy.data.donation.DonationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

@HiltViewModel
class DonateViewModel @Inject constructor(
    private val donationRepository: DonationRepository
) : ViewModel() {

    val donationEvents: SharedFlow<DonationEvent> = donationRepository.donationEvents

    fun donate(activity: Activity, product: DonationProduct) {
        donationRepository.launchDonation(activity, product)
    }
}
