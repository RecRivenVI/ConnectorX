package io.github.recrivenvi.connectorx.ui.remote

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.recrivenvi.connectorx.session.LocalScrollSpeed
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.recrivenvi.connectorx.shared.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor
import kotlin.math.roundToInt

internal object LocalScrollLayout {
    // Preserve mirror fitting from 1.0.8; the wider control never resizes its Surface.
    val MirrorSideSpace=18.dp
    val TouchWidth=48.dp
    val TrackWidth=SliderDefaults.MinHeight+4.dp
    val ThumbWidth=TrackWidth*0.72f
    val ContourPadding=(TrackWidth-ThumbWidth)/2
    val PreferredHeight=204.dp
    val MinimumHeight=156.dp
    val OuterRadius=TrackWidth/2
    val SelectedRadius=ThumbWidth/2
}

/** Three icon-only tabs using Miuix Slider track/knob proportions and one touch owner. */
@Composable
internal fun LocalScrollControl(direction:Int,enabled:Boolean,onDirectionChange:(Int)->Unit,rightGutterWidth:Dp,modifier:Modifier=Modifier) {
    val currentDirection by rememberUpdatedState(direction)
    val currentOnChange by rememberUpdatedState(onDirectionChange)
    val haptics=LocalHapticFeedback.current
    val colors=MiuixTheme.colorScheme
    val selection by animateFloatAsState((1-direction).toFloat(),tween(200),label="Scroll tab")
    val endSpace=((rightGutterWidth-LocalScrollLayout.TouchWidth)/2).coerceAtLeast(0.dp)
    val labels=listOf(stringResource(Res.string.ui_up),stringResource(Res.string.ui_stop),stringResource(Res.string.ui_down))
    // Do not remember ::localFunction: Kotlin function-reference equality ignores captured enabled.
    // In 1.0.9 that retained the initial disabled closure even after the session became ready.
    // Enablement belongs to the input/semantics nodes; this callback only reads live action state.
    val onSelect by rememberUpdatedState<(Int)->Unit>({value->
        currentOnChange(value)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    })
    val trackColor=(if(enabled)colors.sliderBackground else colors.disabledSecondary)
        .compositeOver(colors.surfaceContainer)
    val thumbColor=if(enabled)colors.onPrimary else colors.disabledOnPrimary
    val trackShape=RoundedCornerShape(LocalScrollLayout.OuterRadius)
    val thumbShape=RoundedCornerShape(LocalScrollLayout.SelectedRadius)

    BoxWithConstraints(modifier.width(LocalScrollLayout.TouchWidth).absoluteOffset(x=-endSpace)
        .selectableGroup().semantics {collectionInfo=CollectionInfo(3,1)}
        .pointerInput(enabled) {
            if(!enabled)return@pointerInput
            awaitEachGesture {
                // Own only gestures starting on this control. Initial consumption prevents a
                // selectable child from sending a second click when a drag ends in another tab.
                val down=awaitFirstDown(pass=PointerEventPass.Initial)
                fun directionAt(y:Float):Int {
                    val padding=LocalScrollLayout.ContourPadding.toPx()
                    val itemHeight=((size.height-2*padding)/3f).coerceAtLeast(1f)
                    val index=floor((y-padding)/itemHeight).toInt().coerceIn(0,2)
                    return 1-index
                }
                val startingDirection=currentDirection
                val startingTab=directionAt(down.position.y)
                val activeTap=startingTab!=0&&startingTab==startingDirection
                var tap=true
                var lastDirection=startingDirection
                fun selectAt(y:Float) {
                    val selectedDirection=directionAt(y)
                    // A fast drag may cross multiple tabs before recomposition. Compare with this
                    // gesture's last selection so the center stop cannot be skipped by stale UI state.
                    if(selectedDirection!=lastDirection) {
                        lastDirection=selectedDirection
                        onSelect(selectedDirection)
                    }
                }
                down.consume()
                selectAt(down.position.y)
                do {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    val pointer=event.changes.firstOrNull {it.id==down.id}
                    if(pointer==null)break
                    // A synthetic CANCEL arrives as an already-consumed UP. It must not toggle.
                    if(pointer.isConsumed)break
                    event.changes.forEach {it.consume()}
                    if((pointer.position-down.position).getDistance()>viewConfiguration.touchSlop
                        ||directionAt(pointer.position.y)!=startingTab)tap=false
                    if(!pointer.pressed&&tap&&activeTap&&currentDirection==startingDirection) {
                        onSelect(0)
                    } else selectAt(pointer.position.y)
                } while(pointer.pressed)
                // Only an active-direction tap stops on UP. Drag release retains its last tab.
            }
        }
        ) {
        val tabHeight=(maxHeight-LocalScrollLayout.ContourPadding*2)/3
        Box(Modifier.align(Alignment.Center).width(LocalScrollLayout.TrackWidth).fillMaxHeight()
            .background(trackColor,trackShape))
        Box(Modifier.align(Alignment.TopCenter).width(LocalScrollLayout.ThumbWidth)
            .offset {IntOffset(0,(LocalScrollLayout.ContourPadding.toPx()+selection*tabHeight.toPx()).roundToInt())}
            .height(tabHeight).background(thumbColor,thumbShape))
        Column(Modifier.fillMaxSize().padding(vertical=LocalScrollLayout.ContourPadding)) {
            labels.forEachIndexed {index,label->
                val value=1-index
                val selected=value==direction
                val foreground=if(selected)colors.primary else colors.onSurfaceVariantSummary
                Box(Modifier.fillMaxWidth().weight(1f)
                    .selectable(selected=selected,enabled=enabled,role=Role.Tab,onClick={
                        val target=if(value!=0&&value==direction)0 else value
                        if(target!=direction)onSelect(target)
                    })
                    .semantics {
                        contentDescription=label
                        collectionItemInfo=CollectionItemInfo(index,1,0,1)
                        stateDescription=when {
                            value==0->"停止滚动"
                            selected->"正在滚动，再次点击停止；每秒 ${LocalScrollSpeed.UNITS_PER_SECOND.toInt()} 滚轮单位"
                            else->"点击开始滚动；每秒 ${LocalScrollSpeed.UNITS_PER_SECOND.toInt()} 滚轮单位"
                        }
                    }.graphicsLayer {alpha=if(enabled)1f else 0.38f},contentAlignment=Alignment.Center) {
                    ScrollDirectionIcon(value,foreground)
                }
            }
        }
    }
}

@Composable
private fun ScrollDirectionIcon(direction:Int,color:Color) {
    val image=when {direction>0->MiuixIcons.ChevronBackward;direction<0->MiuixIcons.ChevronForward;else->MiuixIcons.Pause}
    Icon(imageVector=image,contentDescription=null,tint=color,
        modifier=Modifier.size(18.dp).graphicsLayer {rotationZ=if(direction==0)0f else 90f})
}
