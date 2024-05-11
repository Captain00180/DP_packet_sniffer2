package com.dp_packet_sniffer.ui.traffic

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TrafficViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is traffic Fragment"
    }
    val text: LiveData<String> = _text
}