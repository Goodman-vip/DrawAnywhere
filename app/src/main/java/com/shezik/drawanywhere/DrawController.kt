/*
DrawAnywhere: An Android application that lets you draw on top of other apps.
Copyright (C) 2025 shezik

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU Affero General Public License as published by the Free Software
Foundation, either version 3 of the License, or any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along
with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.shezik.drawanywhere

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import com.shezik.drawanywhere.model.DrawAction
import com.shezik.drawanywhere.model.DrawObject
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DrawController {
    private lateinit var penConfig: PenConfig

    fun setPenConfig(config: PenConfig) {
        penConfig = config
    }

    /** Called whenever the path list is modified (add/remove/undo/redo/clear).
     *  Wired by MainService to trigger NativeDrawCanvasView.invalidate(). */
    var onPathsChanged: (() -> Unit)? = null

    private val _strokeList = mutableStateListOf<DrawObject.Stroke>()
    val strokeList: List<DrawObject.Stroke>
        get() = _strokeList

    private val maxUndoDepth = 50
    private val undoStack = mutableListOf<DrawAction>()
    private val redoStack = mutableListOf<DrawAction>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _canClear = MutableStateFlow(false)
    val canClearPaths: StateFlow<Boolean> = _canClear.asStateFlow()

    fun updateLatestStroke(newPoint: Offset) {
        if (!this::penConfig.isInitialized)
            throw IllegalStateException("PenConfig used without initialization!")
        if (penConfig.penType == PenType.StrokeEraser) {
            eraseStroke(newPoint)
            return
        }

        _strokeList.lastOrNull()?.let { stroke ->
            stroke.points.add(newPoint)
            stroke.invalidatePath()
        }
    }

    fun createStroke(newPoint: Offset) {
        if (!this::penConfig.isInitialized)
            throw IllegalStateException("PenConfig used without initialization!")

        if (penConfig.penType == PenType.StrokeEraser) {
            eraseStroke(newPoint)
            return
        }

        _strokeList.add(DrawObject.Stroke(
            points = mutableStateListOf(newPoint),
            color = penConfig.color,
            width = penConfig.width,
            alpha = penConfig.alpha
        ))
    }

    fun finishStroke() {
        if (penConfig.penType == PenType.StrokeEraser) return
        if (_strokeList.isEmpty()) return

        val latest = _strokeList.last()

        if (latest.points.isEmpty()) {
            _strokeList.removeAt(_strokeList.lastIndex)
            return
        }

        redoStack.clear()
        addToUndoStack(DrawAction.AddPath(latest))
        updateUndoRedoState()
        updateClearPathsState()
        onPathsChanged?.invoke()
    }

    private fun eraseStroke(point: Offset) {
        val eraserRadius = penConfig.width / 2
        var indexToErase: Int? = null

        for (i in _strokeList.indices.reversed()) {
            val stroke = _strokeList[i]
            val compensatedRadius = stroke.width / 2 + eraserRadius

            if (stroke.points.size > 1) {
                stroke.points.zipWithNext().forEach { (p1, p2) ->
                    if (distancePointToLineSegment(point, p1, p2) <= compensatedRadius) {
                        indexToErase = i
                        return@forEach
                    }
                }
            } else {
                stroke.points.firstOrNull()?.let {
                    if (distance(point, it) <= compensatedRadius) {
                        indexToErase = i
                    }
                }
            }
            if (indexToErase != null) break
        }

        indexToErase?.let {
            val erased = _strokeList.removeAt(it)
            addToUndoStack(DrawAction.ErasePath(erased))
            erased.releasePath()
            redoStack.clear()
            updateUndoRedoState()
            updateClearPathsState()
            onPathsChanged?.invoke()
        }
    }

    fun clearStrokes() {
        if (_strokeList.isEmpty()) return

        _strokeList.forEach { it.releasePath() }
        addToUndoStack(DrawAction.ClearPaths(_strokeList.toList()))
        _strokeList.clear()
        redoStack.clear()
        updateUndoRedoState()
        updateClearPathsState()
        onPathsChanged?.invoke()
    }

    private fun updateUndoRedoState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun updateClearPathsState() {
        _canClear.value = _strokeList.isNotEmpty()
    }

    private fun addToUndoStack(action: DrawAction) {
        undoStack.add(action)
        if (undoStack.size > maxUndoDepth) {
            undoStack.removeAt(0)
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return

        val action = undoStack.removeAt(undoStack.lastIndex)
        when (action) {
            is DrawAction.AddPath -> {
                val s = action.stroke
                if (_strokeList.remove(s)) {
                    s.releasePath()
                    redoStack.add(action)
                }
            }
            is DrawAction.ErasePath -> {
                _strokeList.add(action.stroke)
                redoStack.add(action)
            }
            is DrawAction.ClearPaths -> {
                _strokeList.addAll(action.strokes)
                redoStack.add(action)
            }
        }
        updateUndoRedoState()
        updateClearPathsState()
        onPathsChanged?.invoke()
    }

    fun redo() {
        if (redoStack.isEmpty()) return

        val action = redoStack.removeAt(redoStack.lastIndex)
        when (action) {
            is DrawAction.AddPath -> {
                _strokeList.add(action.stroke)
                addToUndoStack(action)
            }
            is DrawAction.ErasePath -> {
                if (_strokeList.remove(action.stroke)) {
                    action.stroke.releasePath()
                    addToUndoStack(action)
                }
            }
            is DrawAction.ClearPaths -> {
                _strokeList.removeAll(action.strokes)
                action.strokes.forEach { it.releasePath() }
                addToUndoStack(action)
            }
        }
        updateUndoRedoState()
        updateClearPathsState()
        onPathsChanged?.invoke()
    }
}
