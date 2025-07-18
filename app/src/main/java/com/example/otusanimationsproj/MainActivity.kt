package com.example.otusanimationsproj

import android.animation.Animator
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random


class MainActivity : AppCompatActivity() {
    private var dvdButton: TextView? = null
    private var leftX: Float = 0F
    private var leftY: Float = 0F
    private var endX: Float = 0F
    private var endY: Float = 0F
    private val updateFlow = MutableSharedFlow<Any>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        delayedAnimationsSetup()
        dvdAnimationSetup()
        animatedVectorSetup()
    }

    private fun animatedVectorSetup() {
        findViewById<ImageView>(R.id.animated_vector).setOnClickListener {
            ((it as ImageView).getDrawable() as AnimatedVectorDrawable).start()
        }
    }

    private fun delayedAnimationsSetup() {
        val group = findViewById<ViewGroup>(R.id.delayed_animations)
        val btn1 = group.findViewById<View>(R.id.delayed_btn1)
        val btn2 = group.findViewById<View>(R.id.delayed_btn2)
        btn1.setOnClickListener {
            TransitionManager.beginDelayedTransition(
                findViewById<ViewGroup>(R.id.delayed_animations),
                TransitionSet().apply {
                    ordering = TransitionSet.ORDERING_TOGETHER
                    duration = 1500
                    addTransition(ChangeBounds())
                    addTransition(Fade(Fade.IN))
                    addTransition(Fade(Fade.OUT))
                }
            )
            btn1.visibility = View.INVISIBLE
            btn2.visibility = View.VISIBLE
        }
        btn2.setOnClickListener {
            TransitionManager.beginDelayedTransition(
                findViewById<ViewGroup>(R.id.delayed_animations),
                TransitionSet().apply {
                    ordering = TransitionSet.ORDERING_SEQUENTIAL
                    duration = 1500
                    addTransition(ChangeBounds())
                    addTransition(Fade(Fade.IN))
                    addTransition(Fade(Fade.OUT))
                }
            )
            btn2.visibility = View.INVISIBLE
            btn1.visibility = View.VISIBLE
        }
    }

    /**
     * Чисто по приколу анимацию с плавающим квадратиком DVD из телевизора сделал через MotionLayout
     */
    private fun dvdAnimationSetup() {
        dvdButton = findViewById<TextView>(R.id.dvd_button)
        val parent = dvdButton?.parent as View?
        parent?.getViewTreeObserver()
            ?.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    parent.getViewTreeObserver()?.removeOnGlobalLayoutListener(this)
                    endY =
                        parent.height.toFloat() - resources.getDimensionPixelSize(R.dimen._50dp) - parent.paddingBottom
                    endX =
                        parent.width.toFloat() - resources.getDimensionPixelSize(R.dimen._100dp) - parent.paddingEnd
                    leftX = parent.paddingStart.toFloat()
                    leftY = parent.paddingTop.toFloat()

                    lifecycleScope.launch {
                        updateFlow.emit(
                            MoveToNextLocationEvent(
                                Location(x = leftX + 1, leftY + 1),
                                Velocity(Random.nextFloat() * 10, Random.nextFloat() * 10),
                            )
                        )
                    }
                }
            })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateFlow.collect {
                    if (it is MoveToNextLocationEvent) {
                        runCycle(
                            it.location,
                            it.velocity,
                        )
                    }
                }
            }
        }
    }

    private fun runCycle(loc: Location, velocity: Velocity) {
        val targetLoc = raytracePath(loc, velocity)
        dvdButton!!.setBackgroundColor(Color.argb(255, Random.nextInt(256), Random.nextInt(256), Random.nextInt(256)))
        dvdButton!!.animate()
            .x(targetLoc.atLoc.x)
            .y(targetLoc.atLoc.y)
            .setDuration(5000)
            .setInterpolator(LinearInterpolator()) // BounceInterpolator()
            .setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    val newVelocity = invertVelocity(velocity, targetLoc.collisionDir)

                    lifecycleScope.launch {
                        updateFlow.emit(MoveToNextLocationEvent(
                            targetLoc.atLoc, newVelocity
                        ))
                    }
                }
            })
            .start()
    }

    private fun invertVelocity(velocity: Velocity, dir: Collision): Velocity {
        return velocity.copy(
            x = if (dir.left || dir.right) velocity.x * -1f else velocity.x,
            y = if (dir.top || dir.bottom) velocity.y * -1f else velocity.y,
        )
    }

    @Throws(RuntimeException::class)
    private fun raytracePath(curLoc: Location, curVelocity: Velocity): CollisionResult {
        if (curVelocity.x == 0F && curVelocity.y == 0F) throw RuntimeException("Invalid velocity")

        var x = curLoc.x
        var y = curLoc.y
        do {
            x += curVelocity.x
            y += curVelocity.y
        } while (x >= leftX && x <= endX && y >= leftY && y <= endY)

        return CollisionResult(
            atLoc = Location(x = max(leftX, min(x, endX)), y = max(leftY, min(y, endY))),
            collisionDir = Collision(
                left = x <= leftX,
                top = y <= leftY,
                right = x >= endX,
                bottom = y >= endY,
            ),
        )
    }
}

class MoveToNextLocationEvent(
    val location: Location,
    val velocity: Velocity,
)

data class Collision(
    val left: Boolean = false,
    val top: Boolean = false,
    val right: Boolean = false,
    val bottom: Boolean = false,
)

data class CollisionResult(val atLoc: Location, val collisionDir: Collision)
data class Location(val x: Float, val y: Float)
data class Velocity(val x: Float, val y: Float)
