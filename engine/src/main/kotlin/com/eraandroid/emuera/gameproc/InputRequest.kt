package com.eraandroid.emuera.gameproc

enum class InputType(val code: Int) {
    EnterKey(1), AnyKey(2), IntValue(3), StrValue(4), Void(5), PrimitiveMouseKey(11)
}

class InputRequest {
    val id: Long = lastRequestID++
    var inputType: InputType = InputType.EnterKey
    val needValue: Boolean
        get() = inputType == InputType.IntValue || inputType == InputType.StrValue || inputType == InputType.PrimitiveMouseKey
    var oneInput = false
    var stopMesskip = false
    var isSystemInput = false
    var hasDefValue = false
    var defIntValue: Long = 0
    var defStrValue: String? = null
    var timelimit: Long = -1
    var displayTime = false
    var timeUpMes: String? = null

    companion object {
        private var lastRequestID: Long = 0
    }
}
