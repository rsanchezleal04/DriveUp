package com.example.driveup.location

import android.location.Location
import androidx.lifecycle.MutableLiveData

object LocationBus {
    val location = MutableLiveData<Location>()
}
