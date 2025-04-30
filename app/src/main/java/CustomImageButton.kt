import android.content.Context
import android.graphics.PorterDuff
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.dp_project.dp_packet_sniffer.R

class ColorChangeImageButton : AppCompatImageButton {

    private val normalColor: Int = ContextCompat.getColor(context, R.color.gradient_on_start)
    private val pressedColor: Int = ContextCompat.getColor(context, R.color.gradient_on_end)

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    init {
        setColorFilter(normalColor, PorterDuff.Mode.SRC_ATOP)
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        setColorFilter(if (pressed) pressedColor else normalColor, PorterDuff.Mode.SRC_ATOP)
    }
}
