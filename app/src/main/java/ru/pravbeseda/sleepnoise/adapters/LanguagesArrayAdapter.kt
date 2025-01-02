package ru.pravbeseda.sleepnoise.adapters

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.models.Language

// http://qaru.site/questions/14117683/setmultichoiceitems-with-icon-in-dialog-android
class LanguagesArrayAdapter(context: Context, private val items: Array<Language>) :
    ArrayAdapter<Language>(context, R.layout.item_lang, R.id.text1, items) {
    private val mContext = context

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(R.id.text1)
        var text = mContext.getString(this.items[position].name)
        if (this.items[position].engName.isNotEmpty()) {
            text = "$text\n${this.items[position].engName}"
        }
        textView.text = text
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(this.items[position].flag, 0, 0, 0)
        textView.compoundDrawablePadding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, context.resources.displayMetrics).toInt()
        return view
    }
}
