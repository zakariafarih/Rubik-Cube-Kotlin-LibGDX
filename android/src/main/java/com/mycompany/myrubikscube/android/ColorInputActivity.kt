package com.mycompany.myrubikscube.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mycompany.myrubikscube.R

class ColorInputActivity : AppCompatActivity() {

    private lateinit var gridUp: GridLayout
    private lateinit var gridRight: GridLayout
    private lateinit var gridFront: GridLayout
    private lateinit var gridDown: GridLayout
    private lateinit var gridLeft: GridLayout
    private lateinit var gridBack: GridLayout

    private lateinit var generateSolveButton: Button
    private lateinit var debugCubeStringTextView: TextView

    private val cubieMap = HashMap<Face, Array<CubieButton>>()
    private val faceOrder = listOf(Face.UP, Face.RIGHT, Face.FRONT, Face.DOWN, Face.LEFT, Face.BACK)

    // Track the face currently being scanned.
    private var currentScanningFace: Face? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_color_input_net)

        gridUp = findViewById(R.id.gridUp)
        gridRight = findViewById(R.id.gridRight)
        gridFront = findViewById(R.id.gridFront)
        gridDown = findViewById(R.id.gridDown)
        gridLeft = findViewById(R.id.gridLeft)
        gridBack = findViewById(R.id.gridBack)

        generateSolveButton = findViewById(R.id.generateSolveButton)
        debugCubeStringTextView = findViewById(R.id.debugCubeStringTextView)

        // Initialize each face’s grid with 9 CubieButtons.
        initializeFace(Face.UP, gridUp)
        initializeFace(Face.RIGHT, gridRight)
        initializeFace(Face.FRONT, gridFront)
        initializeFace(Face.DOWN, gridDown)
        initializeFace(Face.LEFT, gridLeft)
        initializeFace(Face.BACK, gridBack)

        generateSolveButton.setOnClickListener {
            val cubeString = generateCubeString()
            if (validateCubeString(cubeString)) {
                sendCubeStringToCubeApp(cubeString)
            } else {
                Toast.makeText(
                    this,
                    "Invalid Cube Configuration. Please ensure all faces are properly colored.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun initializeFace(face: Face, gridLayout: GridLayout) {
        gridLayout.removeAllViews()

        val cubies = Array(9) { index ->
            CubieButton(this, face, index).apply {
                // Use a fixed size (in dp) with proper density conversion.
                val sizeInDp = 80
                val scale = resources.displayMetrics.density
                val sizeInPx = (sizeInDp * scale + 0.5f).toInt()
                layoutParams = GridLayout.LayoutParams().apply {
                    width = sizeInPx
                    height = sizeInPx
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                if (index == 4) {
                    // Set predetermined center color based on face.
                    colorOption = when(face) {
                        Face.UP -> ColorOption.WHITE
                        Face.RIGHT -> ColorOption.RED
                        Face.FRONT -> ColorOption.GREEN
                        Face.DOWN -> ColorOption.YELLOW
                        Face.LEFT -> ColorOption.ORANGE
                        Face.BACK -> ColorOption.BLUE
                    }
                    setBackgroundColor(getColor(colorOption!!.colorResId))
                    // Make center clickable to trigger the scanner.
                    isClickable = true
                    setOnClickListener {
                        launchFaceScanner(face)
                    }
                } else {
                    setBackgroundColor(getColor(R.color.gray))
                    setOnClickListener {
                        showColorPicker(this)
                    }
                }
                contentDescription = "Face: ${face.name}, Position: $index"
            }
        }
        cubies.forEach { gridLayout.addView(it) }
        cubieMap[face] = cubies
        checkGenerateButtonState()
    }

    private fun launchFaceScanner(face: Face) {
        currentScanningFace = face
        val forcedCenterChar = when(face) {
            Face.UP -> 'U'
            Face.RIGHT -> 'R'
            Face.FRONT -> 'F'
            Face.DOWN -> 'D'
            Face.LEFT -> 'L'
            Face.BACK -> 'B'
        }
        val intent = Intent(this, FaceScanActivity::class.java)
        intent.putExtra("FORCE_CENTER", forcedCenterChar)
        startActivityForResult(intent, FACE_SCAN_REQUEST_CODE)
    }

    companion object {
        const val FACE_SCAN_REQUEST_CODE = 1001
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FACE_SCAN_REQUEST_CODE && resultCode == RESULT_OK) {
            val faceString = data?.getStringExtra("FACE_STRING")
            if (faceString != null && faceString.length == 9) {
                currentScanningFace?.let { face ->
                    updateFaceWithScannedData(face, faceString)
                } ?: run {
                    Toast.makeText(this, "Unknown face scanned. Try again.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Error scanning face. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFaceWithScannedData(face: Face, faceString: String) {
        val cubies = cubieMap[face] ?: return
        faceString.forEachIndexed { index, c ->
            val colorOption = ColorOption.values().find { it.getChar() == c }
            if (colorOption != null) {
                cubies[index].setBackgroundColor(getColor(colorOption.colorResId))
                cubies[index].colorOption = colorOption
            }
        }
        checkGenerateButtonState()
    }

    private fun showColorPicker(cubieButton: CubieButton) {
        val colorPicker = ColorPickerDialog(this, object : ColorPickerDialog.OnColorSelectedListener {
            override fun onColorSelected(colorOption: ColorOption) {
                cubieButton.setBackgroundColor(getColor(colorOption.colorResId))
                cubieButton.colorOption = colorOption
                Log.d("ColorInputActivity", "CubieButton at face ${cubieButton.face?.name}, position ${cubieButton.position} set to $colorOption")
                checkGenerateButtonState()
            }
        })
        colorPicker.show()
    }

    private fun generateCubeString(): String {
        val sb = StringBuilder()
        for (face in faceOrder) {
            val cubies = cubieMap[face]
            if (cubies != null) {
                for (cubie in cubies) {
                    val colorOption = cubie.colorOption
                    if (colorOption != null) {
                        sb.append(colorOption.getChar())
                    } else {
                        sb.append('X')
                    }
                }
            } else {
                sb.append("XXXXXXXXX")
            }
        }
        Log.d("ColorInputActivity", "Generated cube string: $sb")
        debugCubeStringTextView.text = "Cube String: $sb"
        return sb.toString()
    }

    private fun validateCubeString(cubeString: String): Boolean {
        if (cubeString.length != 54) {
            Log.e("ColorInputActivity", "Cube string length is not 54: $cubeString")
            return false
        }
        val colorCount = HashMap<Char, Int>()
        for (c in cubeString.toCharArray()) {
            if (c == 'X') {
                Log.e("ColorInputActivity", "Cube string contains undefined color 'X'")
                return false
            }
            colorCount[c] = colorCount.getOrDefault(c, 0) + 1
        }
        for (colorOption in ColorOption.values()) {
            val expectedChar = colorOption.getChar()
            if (colorCount[expectedChar] != 9) {
                Log.e("ColorInputActivity", "Color $expectedChar count ${colorCount[expectedChar]}, expected 9")
                return false
            }
        }
        Log.d("ColorInputActivity", "Cube string is valid.")
        return true
    }

    private fun sendCubeStringToCubeApp(cubeString: String) {
        val resultIntent = Intent().apply {
            putExtra("CUBE_STRING", cubeString)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun checkGenerateButtonState() {
        for (face in faceOrder) {
            val cubies = cubieMap[face]
            if (cubies == null || cubies.any { it.colorOption == null }) {
                generateSolveButton.isEnabled = false
                return
            }
            if (cubies[4].colorOption == null) {
                generateSolveButton.isEnabled = false
                return
            }
        }
        generateSolveButton.isEnabled = true
    }
}
