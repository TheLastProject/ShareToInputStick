package me.hackerchick.sharetoinputstick

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class InputStickAdapter : ArrayAdapter<InputStick> {
    private var inputStickList = ArrayList<InputStick>()
    private var inputStickViewModel : InputStickViewModel? = null

    constructor(context: Context, items: ArrayList<InputStick>, viewModel: InputStickViewModel?) : super(context, 0, items) {
        inputStickList = items
        inputStickViewModel = viewModel
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inputStick = getItem(position)!!

        var view = convertView

        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.inputstick_list_item, parent, false)
        }

        val tvName = view?.findViewById(R.id.name) as TextView
        val tvMac = view.findViewById(R.id.mac) as TextView
        val iconBluetooth = view.findViewById(R.id.bluetoothIcon) as ImageView

        tvName.text = inputStick.name
        tvMac.text = inputStick.mac
        iconBluetooth.visibility = if (inputStickViewModel?.bluetoothDevicesListContains(inputStick) == true) View.VISIBLE else View.GONE

        return view
    }
}