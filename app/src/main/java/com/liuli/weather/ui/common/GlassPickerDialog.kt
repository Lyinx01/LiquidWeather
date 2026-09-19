package com.liuli.weather.ui.common

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.liquidglass.LiquidGlassView
import com.liuli.weather.R
import com.liuli.weather.databinding.DialogGlassPickerBinding

/**
 * Liquid Glass 风格的单选弹窗。
 *
 * Dialog 是独立窗口，玻璃默认只能捕获自己所在窗口的内容（为空），
 * 因此必须显式把 [LiquidGlassView.backdropSource] 指向宿主 Activity 的
 * content view，才能折射出设置页背后的画面。
 */
object GlassPickerDialog {

    /**
     * @param title 标题
     * @param options 选项文案
     * @param checkedIndex 当前选中项（-1 表示无选中）
     * @param onPick 选中回调
     */
    fun show(
        activity: Activity,
        title: CharSequence,
        options: List<CharSequence>,
        checkedIndex: Int,
        onPick: (Int) -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val binding = DialogGlassPickerBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)

        // 窗口本身完全透明，视觉全部交给玻璃层
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.35f)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            attributes = attributes.apply {
                windowAnimations = R.style.GlassDialogAnimation
            }
        }

        // 关键：让玻璃捕获宿主 Activity 的内容作为背景
        binding.glassPicker.backdropSource =
            activity.window?.decorView?.findViewById(android.R.id.content)
        binding.glassPicker.enableDynamicBackground = true

        binding.tvPickerTitle.text = title
        buildOptions(activity, binding.pickerOptions, options, checkedIndex) { index ->
            onPick(index)
            dialog.dismiss()
        }

        dialog.show()

        // 弹窗出现后重新指定一次（此时 decorView 已挂载，坐标换算才准确）
        binding.glassPicker.backdropSource =
            activity.window?.decorView?.findViewById(android.R.id.content)
        binding.glassPicker.invalidate()
    }

    private fun buildOptions(
        activity: Activity,
        container: LinearLayout,
        options: List<CharSequence>,
        checkedIndex: Int,
        onClick: (Int) -> Unit
    ) {
        container.removeAllViews()
        options.forEachIndexed { index, label ->
            val row = LayoutInflater.from(activity)
                .inflate(R.layout.item_glass_option, container, false)
            row.findViewById<TextView>(R.id.tv_option).text = label
            row.findViewById<ImageView>(R.id.iv_check).visibility =
                if (index == checkedIndex) View.VISIBLE else View.INVISIBLE
            row.setOnClickListener { onClick(index) }
            container.addView(row)
        }
    }
}
