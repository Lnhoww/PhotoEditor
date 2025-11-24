package com.example.photoeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import androidx.compose.ui.geometry.Rect // Import Rect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ImageRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private val vertexShaderCode =
        "attribute vec4 vPosition;" +
        "attribute vec2 vTexCoord;" +
        "uniform float uScale;" +
        "uniform vec2 uTranslate;" +
        "varying vec2 TexCoord;" +
        "void main() {" +
        "  gl_Position = vec4(vPosition.xy * uScale + uTranslate, vPosition.zw);" +
        "  TexCoord = vTexCoord;" +
        "}"

    private val fragmentShaderCode =
        "precision mediump float;" +
        "uniform sampler2D uTexture;" +
        "varying vec2 TexCoord;" +
        "void main() {" +
        "  gl_FragColor = texture2D(uTexture, TexCoord);" +
        "}"

    private var program: Int = 0
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBuffer: FloatBuffer
    private val textureIds = IntArray(1)

    private val quadVertices = floatArrayOf( -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f )
    private val defaultTexCoords = floatArrayOf( 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f )

    @Volatile var currentScale = 1.0f
    @Volatile var currentTranslateX = 0.0f
    @Volatile var currentTranslateY = 0.0f

    private var _imageUri: Uri? = null
    private var currentLoadedUri: Uri? = null
    var surfaceView: GLSurfaceView? = null

    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    @Volatile private var appliedCropRect: Rect? = null // Store crop rect in NDC

    private val fbo = IntArray(1)
    private val renderBuffer = IntArray(1)
    private val fboTexture = IntArray(1)
    private var fboWidth = 0
    private var fboHeight = 0

    fun setImageUri(uri: Uri, glSurfaceView: GLSurfaceView) {
        _imageUri = uri
        surfaceView = glSurfaceView
        if (uri != currentLoadedUri) {
            currentScale = 1.0f
            currentTranslateX = 0.0f
            currentTranslateY = 0.0f
            appliedCropRect = null
        }
        glSurfaceView.requestRender()
    }

    fun setScale(scale: Float) {
        currentScale = scale
        surfaceView?.requestRender()
    }

    fun setTranslation(x: Float, y: Float) {
        currentTranslateX = x
        currentTranslateY = y
        surfaceView?.requestRender()
    }

    fun setAppliedCropRect(rect: Rect?) {
        appliedCropRect = rect
        surfaceView?.requestRender()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        val bb = ByteBuffer.allocateDirect(quadVertices.size * 4).order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer().apply { put(quadVertices); position(0) }

        val tcb = ByteBuffer.allocateDirect(defaultTexCoords.size * 4).order(ByteOrder.nativeOrder())
        texCoordBuffer = tcb.asFloatBuffer().apply { put(defaultTexCoords); position(0) }

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        GLES20.glGenTextures(1, textureIds, 0)
        currentLoadedUri = null
    }

    override fun onDrawFrame(gl: GL10?) {
        drawScene(0, viewportWidth, viewportHeight) // Draw to default framebuffer
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
        setupOffscreenRender(width, height) // Setup FBO with current surface size
        surfaceView?.requestRender()
    }

    private fun drawScene(targetFramebuffer: Int, targetWidth: Int, targetHeight: Int) {
        if (targetFramebuffer != 0) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFramebuffer)
            GLES20.glViewport(0, 0, targetWidth, targetHeight)
        } else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        }

        _imageUri?.let { uri ->
            if (uri != currentLoadedUri) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        imageWidth = bitmap.width
                        imageHeight = bitmap.height
                        loadTexture(bitmap)
                        bitmap.recycle()
                        currentLoadedUri = uri
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    currentLoadedUri = null
                }
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        // Calculate aspect ratio scales for the image within the viewport
        var effectiveScaleX = 1.0f
        var effectiveScaleY = 1.0f
        if (targetWidth != 0 && targetHeight != 0 && imageWidth != 0 && imageHeight != 0) {
            val viewportRatio = targetWidth.toFloat() / targetHeight.toFloat()
            val imageRatio = imageWidth.toFloat() / imageHeight.toFloat()

            if (imageRatio > viewportRatio) {
                effectiveScaleY = viewportRatio / imageRatio
            } else {
                effectiveScaleX = imageRatio / viewportRatio
            }
        }

        // The NDC bounds of the displayed image within the GLSurfaceView
        val imgNdcLeft = -effectiveScaleX
        val imgNdcRight = effectiveScaleX
        val imgNdcBottom = -effectiveScaleY
        val imgNdcTop = effectiveScaleY

        val currentTexCoords = if (appliedCropRect != null) {
            // Map appliedCropRect (GLSurfaceView NDC) to relative to image NDC bounds
            // Then map that relative range to the 0-1 texture range.

            val cropLeftRelToImg = (appliedCropRect!!.left - imgNdcLeft) / (imgNdcRight - imgNdcLeft)
            val cropRightRelToImg = (appliedCropRect!!.right - imgNdcLeft) / (imgNdcRight - imgNdcLeft)
            // For Y, NDC Y=1 (top) maps to Texture Y=0 (top)
            // NDC Y=-1 (bottom) maps to Texture Y=1 (bottom)
            val cropTopRelToImg = (imgNdcTop - appliedCropRect!!.top) / (imgNdcTop - imgNdcBottom)
            val cropBottomRelToImg = (imgNdcTop - appliedCropRect!!.bottom) / (imgNdcTop - imgNdcBottom)

            floatArrayOf(
                cropLeftRelToImg, cropBottomRelToImg,  // Bottom Left
                cropRightRelToImg, cropBottomRelToImg, // Bottom Right
                cropLeftRelToImg, cropTopRelToImg,     // Top Left
                cropRightRelToImg, cropTopRelToImg      // Top Right
            )
        } else {
            defaultTexCoords
        }
        texCoordBuffer.put(currentTexCoords).position(0)
        
        val adjustedVertices = calculateAdjustedVertices()
        vertexBuffer.put(adjustedVertices).position(0)

        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)

        val texCoordHandle = GLES20.glGetAttribLocation(program, "vTexCoord")
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)
        
        val scaleHandle = GLES20.glGetUniformLocation(program, "uScale")
        val translateHandle = GLES20.glGetUniformLocation(program, "uTranslate")
        val textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        
        GLES20.glUniform1f(scaleHandle, currentScale)
        GLES20.glUniform2f(translateHandle, currentTranslateX, currentTranslateY)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
        GLES20.glUniform1i(textureHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)

        if (targetFramebuffer != 0) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0) // Unbind FBO, switch back to default
        }
    }

    private fun setupOffscreenRender(width: Int, height: Int) {
        if (fboWidth == width && fboHeight == height && fbo[0] != 0) {
            return // FBO already set up with correct size
        }

        fboWidth = width
        fboHeight = height

        // Delete old FBO if exists
        if (fbo[0] != 0) {
            GLES20.glDeleteFramebuffers(1, fbo, 0)
            GLES20.glDeleteTextures(1, fboTexture, 0)
            GLES20.glDeleteRenderbuffers(1, renderBuffer, 0)
        }

        // Generate FBO
        GLES20.glGenFramebuffers(1, fbo, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])

        // Generate texture for FBO color attachment
        GLES20.glGenTextures(1, fboTexture, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTexture[0])
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, fboWidth, fboHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTexture[0], 0)

        // Generate renderbuffer for depth/stencil attachment
        GLES20.glGenRenderbuffers(1, renderBuffer, 0)
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, renderBuffer[0])
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, fboWidth, fboHeight)
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, renderBuffer[0])

        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            // Handle FBO creation error
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0) // Unbind FBO
    }

    fun captureRenderedBitmap(): Bitmap? {
        if (fbo[0] == 0 || fboWidth == 0 || fboHeight == 0) {
            return null
        }

        var resultBitmap: Bitmap? = null
        // For simplicity and immediate return, we'll try to draw directly here
        // and then read pixels. This might lead to issues if not on GL thread.
        // A proper solution involves synchronization (e.g., a CountDownLatch).

        // Execute capture on GL thread
        val tempBuffer = ByteBuffer.allocateDirect(fboWidth * fboHeight * 4)
        tempBuffer.order(ByteOrder.nativeOrder())

        drawScene(fbo[0], fboWidth, fboHeight) // Render to FBO immediately

        GLES20.glReadPixels(0, 0, fboWidth, fboHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, tempBuffer)
        tempBuffer.position(0)

        val rawBitmap = Bitmap.createBitmap(fboWidth, fboHeight, Bitmap.Config.ARGB_8888)
        rawBitmap.copyPixelsFromBuffer(tempBuffer)

        val matrix = android.graphics.Matrix().apply { postScale(1f, -1f, fboWidth / 2f, fboHeight / 2f) }
        resultBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, fboWidth, fboHeight, matrix, true)
        rawBitmap.recycle() // Recycle the raw bitmap

        return resultBitmap
    }

    private fun calculateAdjustedVertices(): FloatArray {
        if (viewportWidth == 0 || viewportHeight == 0 || imageWidth == 0 || imageHeight == 0) {
            return quadVertices
        }

        val viewportRatio = viewportWidth.toFloat() / viewportHeight.toFloat()
        val imageRatio = imageWidth.toFloat() / imageHeight.toFloat()

        var scaleX = 1.0f
        var scaleY = 1.0f

        if (imageRatio > viewportRatio) {
            scaleY = viewportRatio / imageRatio
        } else {
            scaleX = imageRatio / viewportRatio
        }

        return floatArrayOf(-scaleX, -scaleY, scaleX, -scaleY, -scaleX, scaleY, scaleX, scaleY)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    private fun loadTexture(bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    }
}
