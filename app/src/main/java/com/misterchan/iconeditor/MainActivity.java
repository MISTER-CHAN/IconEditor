package com.misterchan.iconeditor;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputFilter;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private final class FloodFill extends Thread {

        int x, y, color;

        private FloodFill(int x, int y, int color) {
            this.x = x;
            this.y = y;
            this.color = color;
        }

        @Override
        public void run() {
            int pixel = bitmap.getPixel(x, y);
            if (pixel == color) {
                return;
            }
            int left, top, right, bottom;
            if (hasSelection) {
                left = selection.left;
                top = selection.top;
                right = selection.right;
                bottom = selection.bottom;
            } else {
                left = 0;
                top = 0;
                right = bitmap.getWidth() - 1;
                bottom = bitmap.getHeight() - 1;
            }
            if (!(left <= x && x <= right && top <= y && y <= bottom)) {
                return;
            }
            Timer timer = new Timer();
            timer.schedule(new ViewBitmapTask(), 50L, 50L);
            Queue<Point> pointsToBeSet = new LinkedList<>();
            boolean[][] havePointsBeenSet = new boolean[right - left + 1][bottom - top + 1];
            pointsToBeSet.offer(new Point(x, y));
            Point point;
            while ((point = pointsToBeSet.poll()) != null) {
                int px = bitmap.getPixel(point.x, point.y);
                int xr = point.x - left, yr = point.y - top; // r - relative
                havePointsBeenSet[xr][yr] = true;
                if (px == pixel && px != color) {
                    bitmap.setPixel(point.x, point.y, color);
                    int xn = point.x - 1, xp = point.x + 1, yn = point.y - 1, yp = point.y + 1; // n - negative, p - positive
                    if (left <= xn && !havePointsBeenSet[xn - left][yr])
                        pointsToBeSet.offer(new Point(xn, point.y));
                    if (xp <= right && !havePointsBeenSet[xp - left][yr])
                        pointsToBeSet.offer(new Point(xp, point.y));
                    if (top <= yn && !havePointsBeenSet[xr][yn - top])
                        pointsToBeSet.offer(new Point(point.x, yn));
                    if (yp <= bottom && !havePointsBeenSet[xr][yp - top])
                        pointsToBeSet.offer(new Point(point.x, yp));
                }
            }
            timer.cancel();
            timer.purge();
            drawBitmapOnView();
            history.offer(bitmap);
        }
    }

    private final class ViewBitmapTask extends TimerTask {
        @Override
        public void run() {
            drawBitmapOnCanvas(bitmap, window.translationX, window.translationY, viewCanvas);
            imageView.invalidate();
        }
    }

    private static final Pattern PATTERN_FILE_NAME = Pattern.compile("[\"*/:<>?\\\\|]");
    private static final Pattern PATTERN_TREE = Pattern.compile("^content://com\\.android\\.externalstorage\\.documents/tree/primary%3A(?<path>.*)$");

    private static final String FORMAT_02X = "%02X";

    private static final Bitmap.CompressFormat[] COMPRESS_FORMATS = {
            Bitmap.CompressFormat.PNG,
            Bitmap.CompressFormat.JPEG
    };

    private static final InputFilter[] FILE_NAME_FILTERS = new InputFilter[]{
            (source, start, end, dest, dstart, dend) -> {
                Matcher matcher = PATTERN_FILE_NAME.matcher(source.toString());
                if (matcher.find()) {
                    return "";
                }
                return null;
            }
    };

    private AfterTextChangedListener onPropSizeXTextChangedListener, onPropSizeYTextChangedListener;
    private Bitmap bitmap;
    private Bitmap chessboard;
    private Bitmap chessboardBitmap;
    private Bitmap clipboard;
    private Bitmap gridBitmap;
    private Bitmap previewBitmap;
    private Bitmap transformeeBitmap;
    private Bitmap selectionBitmap;
    private Bitmap viewBitmap;
    private BitmapHistory history;
    private boolean hasNotLoaded = true;
    private boolean hasSelection = false;
    private Canvas canvas;
    private Canvas chessboardCanvas;
    private Canvas gridCanvas;
    private Canvas previewCanvas;
    private Canvas selectionCanvas;
    private Canvas viewCanvas;
    private CellGrid cellGrid;
    private CheckBox cbBucketFillContiguous;
    private CheckBox cbCellGridEnabled;
    private CheckBox cbPropLar;
    private CheckBox cbScaler;
    private CheckBox cbTransformerFill;
    private CheckBox cbTransformerLar;
    private double prevDiagonal;
    private double transformeeAspectRatio;
    private EditText etCellGridOffsetX, etCellGridOffsetY;
    private EditText etCellGridSizeX, etCellGridSizeY;
    private EditText etCellGridSpacingX, etCellGridSpacingY;
    private EditText etEraserStrokeWidth;
    private EditText etFileName;
    private EditText etNewGraphicSizeX, etNewGraphicSizeY;
    private EditText etPencilStrokeWidth;
    private EditText etPropSizeX, etPropSizeY;
    private EditText etRed, etGreen, etBlue, etAlpha;
    private EditText etText;
    private EditText etTextSize;
    private float pivotX, pivotY;
    private float prevX, prevY;
    private float transformeeTranslationX, transformeeTranslationY;
    private FrameLayout flImageView;
    private ImageView imageView;
    private ImageView ivChessboard;
    private ImageView ivGrid;
    private ImageView ivPreview;
    private ImageView ivSelection;
    private InputMethodManager inputMethodManager;
    private int currentBitmapIndex;
    private int imageWidth, imageHeight;
    private int rectX, rectY;
    private int selectionStartX, selectionStartY;
    private int selectionEndX, selectionEndY;
    private int textX, textY;
    private int viewWidth, viewHeight;
    private LinearLayout llBehaviorBucketFill;
    private LinearLayout llBehaviorEraser;
    private LinearLayout llBehaviorPencil;
    private LinearLayout llBehaviorText;
    private LinearLayout llBehaviorTransformer;
    private List<Window> windows = new ArrayList<>();
    private Positions selection = new Positions();
    private PositionsF transfromeeDpb = new PositionsF(); // DPB - Distance from point to bounds
    private RadioButton rbColor;
    private RadioButton rbBackgroundColor;
    private RadioButton rbForegroundColor;
    private RadioButton rbPropStretch, rbPropCrop;
    private RadioButton rbTransformer;
    private SeekBar sbRed, sbGreen, sbBlue, sbAlpha;
    private Spinner sFileType;
    private String tree = "";
    private TabLayout tabLayout;
    private TextView tvStatus;
    private Window window;

    private final Paint backgroundPaint = new Paint() {

        {
            setAntiAlias(false);
            setColor(Color.WHITE);
            setDither(false);
            setTextAlign(Paint.Align.CENTER);
        }
    };

    private final Paint cellGridPaint = new Paint() {

        {
            setColor(Color.RED);
            setStrokeWidth(2.0f);
        }
    };

    private final Paint colorPaint = new Paint() {

        {
            setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        }
    };

    private final Paint eraser = new Paint() {

        {
            setAntiAlias(false);
            setColor(Color.BLACK);
            setDither(false);
            setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }
    };

    private final Paint foregroundPaint = new Paint() {

        {
            setAntiAlias(false);
            setColor(Color.BLACK);
            setDither(false);
            setTextAlign(Paint.Align.CENTER);
        }
    };

    private final Paint gridPaint = new Paint() {

        {
            setColor(Color.GRAY);
        }
    };

    private final Paint opaquePaint = new Paint() {

        {
            setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        }
    };

    private final Paint pointPaint = new Paint() {

        {
            setColor(Color.RED);
            setStrokeWidth(4.0f);
            setTextSize(32.0f);
        }
    };

    private final Paint selector = new Paint() {

        {
            setColor(Color.DKGRAY);
            setStrokeWidth(4.0f);
            setStyle(Style.STROKE);
        }
    };

    private Paint paint = foregroundPaint;

    private final CompoundButton.OnCheckedChangeListener onBackgroundColorRadioButtonCheckedChangeListener = (buttonView, isChecked) -> {
        if (isChecked) {
            paint = backgroundPaint;
            rbColor = rbBackgroundColor;
            showPaintColorOnSeekBars();
        }
    };

    private final CompoundButton.OnCheckedChangeListener onForegroundColorRadioButtonCheckedChangeListener = (buttonView, isChecked) -> {
        if (isChecked) {
            paint = foregroundPaint;
            rbColor = rbForegroundColor;
            showPaintColorOnSeekBars();
        }
    };

    private final CompoundButton.OnCheckedChangeListener onPropLarCheckBoxCheckedChangeListener = (buttonView, isChecked) -> {
        if (isChecked) {
            etPropSizeX.addTextChangedListener(onPropSizeXTextChangedListener);
            etPropSizeY.addTextChangedListener(onPropSizeYTextChangedListener);
        } else {
            etPropSizeX.removeTextChangedListener(onPropSizeXTextChangedListener);
            etPropSizeY.removeTextChangedListener(onPropSizeYTextChangedListener);
        }
    };

    private final DialogInterface.OnClickListener onCellGridDialogPosButtonClickListener = (dialog, which) -> {
        try {
            int sizeX = Integer.parseInt(etCellGridSizeX.getText().toString()),
                    sizeY = Integer.parseInt(etCellGridSizeY.getText().toString()),
                    spacingX = Integer.parseInt(etCellGridSpacingX.getText().toString()),
                    spacingY = Integer.parseInt(etCellGridSpacingY.getText().toString()),
                    offsetX = Integer.parseInt(etCellGridOffsetX.getText().toString()),
                    offsetY = Integer.parseInt(etCellGridOffsetY.getText().toString());
            cellGrid.enabled = cbCellGridEnabled.isChecked();
            cellGrid.sizeX = sizeX;
            cellGrid.sizeY = sizeY;
            cellGrid.spacingX = spacingX;
            cellGrid.spacingY = spacingY;
            cellGrid.offsetX = offsetX;
            cellGrid.offsetY = offsetY;
        } catch (NumberFormatException e) {
        }
        drawGridOnView();
    };

    private final DialogInterface.OnClickListener onFileNameDialogPosButtonClickListener = (dialog, which) -> {
        String fileName = etFileName.getText().toString();
        if ("".equals(fileName)) {
            return;
        }
        fileName += sFileType.getSelectedItem().toString();
        window.path = Environment.getExternalStorageDirectory().getPath() + File.separator + tree + File.separator + fileName;
        window.compressFormat = COMPRESS_FORMATS[sFileType.getSelectedItemPosition()];
        save(window.path);
        tabLayout.getTabAt(tabLayout.getSelectedTabPosition()).setText(fileName);
    };

    private final DialogInterface.OnClickListener onNewGraphicDialogPosButtonClickListener = (dialog, which) -> {
        try {
            int width = Integer.parseUnsignedInt(etNewGraphicSizeX.getText().toString());
            int height = Integer.parseUnsignedInt(etNewGraphicSizeY.getText().toString());
            createGraphic(width, height);
        } catch (NumberFormatException e) {
        }
    };

    private final DialogInterface.OnClickListener onPropDialogPosButtonClickListener = (dialog, which) -> {
        try {
            int width = Integer.parseUnsignedInt(etPropSizeX.getText().toString());
            int height = Integer.parseUnsignedInt(etPropSizeY.getText().toString());
            boolean stretch = rbPropStretch.isChecked();
            resizeBitmap(width, height, stretch);
            drawBitmapOnView();
            history.offer(bitmap);
        } catch (NumberFormatException e) {
        }
    };

    private final ActivityResultCallback<Uri> imageCallback = result -> {
        if (result == null) {
            return;
        }
        try (InputStream inputStream = getContentResolver().openInputStream(result)) {
            Bitmap bm = BitmapFactory.decodeStream(inputStream);
            openFile(bm, result);
            bm.recycle();

        } catch (IOException e) {
            e.printStackTrace();
        }
    };

    private final ActivityResultCallback<Uri> treeCallback = result -> {
        if (result == null) {
            return;
        }
        Matcher matcher = PATTERN_TREE.matcher(result.toString());
        if (!matcher.find()) {
            return;
        }
        tree = matcher.group("path").replace("%2F", "/");
        AlertDialog fileNameDialog = new AlertDialog.Builder(this)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, onFileNameDialogPosButtonClickListener)
                .setTitle(R.string.file_name)
                .setView(R.layout.file_name)
                .show();

        etFileName = fileNameDialog.findViewById(R.id.et_file_name);
        sFileType = fileNameDialog.findViewById(R.id.s_file_type);

        etFileName.setFilters(FILE_NAME_FILTERS);
        sFileType.setAdapter(new ArrayAdapter<>(this, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, getResources().getStringArray(R.array.file_types)));
    };

    private final ActivityResultLauncher<String> getImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), imageCallback);

    private final ActivityResultLauncher<Uri> getTree =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), treeCallback);

    private final TabLayout.OnTabSelectedListener onTabSelectedListener = new TabLayout.OnTabSelectedListener() {
        @Override
        public void onTabSelected(TabLayout.Tab tab) {
            drawTransformeeOnCanvas();
            drawTextOnCanvas();

            currentBitmapIndex = tab.getPosition();
            window = windows.get(currentBitmapIndex);
            bitmap = window.bitmap;
            canvas = new Canvas(bitmap);
            history = window.history;
            cellGrid = window.cellGrid;

            int width = bitmap.getWidth(), height = bitmap.getHeight();
            imageWidth = (int) toScaled(width);
            imageHeight = (int) toScaled(height);

            recycleBitmapIfIsNotNull(transformeeBitmap);
            transformeeBitmap = null;
            hasSelection = false;

            drawChessboardOnView();
            drawBitmapOnView();
            drawGridOnView();
            drawSelectionOnView();
            clearCanvasAndInvalidateView(previewCanvas, ivPreview);
        }

        @Override
        public void onTabUnselected(TabLayout.Tab tab) {
        }

        @Override
        public void onTabReselected(TabLayout.Tab tab) {
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    private final View.OnTouchListener onImageViewTouchWithBucketListener = (v, event) -> {
        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN: {
                float x = event.getX(), y = event.getY();
                int originalX = toOriginal(x - window.translationX), originalY = toOriginal(y - window.translationY);
                if (!(0 <= originalX && originalX < bitmap.getWidth() && 0 <= originalY && originalY < bitmap.getHeight())) {
                    break;
                }
                if (cbBucketFillContiguous.isChecked()) {
                    floodFill(originalX, originalY, paint.getColor());
                } else {
                    bucketFill(originalX, originalY, paint.getColor());
                }
                tvStatus.setText("");
                break;
            }
        }
        return true;
    };

    @SuppressLint("ClickableViewAccessibility")
    private final View.OnTouchListener onImageViewTouchWithEraserListener = (v, event) -> {
        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN: {
                float x = event.getX(), y = event.getY();
                int originalX = toOriginal(x - window.translationX), originalY = toOriginal(y - window.translationY);
                canvas.drawPoint(originalX, originalY, eraser);
                drawBitmapOnView();
                tvStatus.setText(String.format("(%d, %d)", originalX, originalY));
                prevX = x;
                prevY = y;
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                float x = event.getX(), y = event.getY();
                int originalX = toOriginal(x - window.translationX), originalY = toOriginal(y - window.translationY);
                drawLineOnCanvas(
                        toOriginal(prevX - window.translationX),
                        toOriginal(prevY - window.translationY),
                        originalX,
                        originalY,
                        eraser);
                drawBitmapOnView();
                tvStatus.setText(String.format("(%d, %d)", originalX, originalY));
                prevX = x;
                prevY = y;
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                history.offer(bitmap);
                tvStatus.setText("");
                break;
        }
        return true;
    };

    @SuppressLint("ClickableViewAccessibility")
    private final View.OnTouchListener onImageViewTouchWithEyedropperListener = (v, event) -> {
        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                float x = event.getX(), y = event.getY();
                int originalX = toOriginal(x - window.translationX), originalY = toOriginal(y - window.translationY);
                paint.setColor(bitmap.getPixel(originalX, originalY));
                showPaintColorOnSeekBars();
                tvStatus.setText(String.format("(%d, %d)", originalX, originalY));
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                tvStatus.setText("");
                break;
        }
        return true;
    };

    @SuppressLint("ClickableViewAccessibility")
    private final View.OnTouchListener onImageViewTouchWithPencilListener = (v, event) -> {
        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN: {
                float x = event.getX(), y = event.getY();
                int originalX = toOriginal(x - window.translationX), originalY = toOriginal(y - window.translationY);
                canvas.drawPoint(originalX, originalY, paint);
                drawBitmapOnView();
                tvStatus.setText(String.format("(%d, %d)", originalX, originalY));
                prevX = x;
                prevY = y;
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                float x = event.getX(), y = event.getY();
                int originalX = toOriginal(x - window.translationX), originalY = toOriginal(y - window.translationY);
                drawLineOnCanvas(
                        toOriginal(prevX - window.translationX),
                        toOriginal(prevY - window.translationY),
                        originalX,
                        originalY,
                        paint);
                drawBitmapOnView();
                tvStatus.setText(String.format("(%d, %d)", originalX, originalY));
                prevX = x;
                prevY = y;
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                history.offer(bitmap);
                tvStatus.setText("");
                break;
        }
        return true;
    };

    @SuppressLint("ClickableViewAccessibility")
    private final View.OnTouchListener onImageViewTouchWithRectListener = (v, event) -> {
        float x = event.getX(), y = event.getY();
        int originalX = toOriginal(x - window.translationX), originalY = toOriginal(y - window.translationY);
        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN: {
                drawRectOnView(originalX, originalY, originalX, originalY);
                rectX = originalX;
                rectY = originalY;
                tvStatus.setText(String.format("Start: (%d, %d), Stop: (%d, %d), Area: 1 ?? 1",
                        originalX, originalY, originalX, originalY));
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                drawRectOnView(rectX, rectY, originalX, originalY);
                tvStatus.setText(String.format("Start: (%d, %d), Stop: (%d, %d), Area: %d ?? %d",
                        rectX, rectY, originalX, originalY,
                        Math.abs(originalX - rectX) + 1, Math.abs(originalY - rectY) + 1));
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                drawRectOnCanvas(rectX, rectY, originalX, originalY);
                drawBitmapOnView();
                clearCanvasAndInvalidateView(previewCanvas, ivPreview);
                history.offer(bitmap);
                tvStatus.setText("");
                break;
        }
        return true;
    };

    @SuppressLint("ClickableViewAccessibility")
    private final View.OnTouchListener onImageViewTouchWithScalerListener = (v, event) -> {
        switch (event.getPointerCount()) {

            case 1: {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        float x = event.getX(), y = event.getY();
                        tvStatus.setText(String.format("(%d, %d)",
                                toOriginal(x - window.translationX), toOriginal(y - window.translationY)));
                        prevX = x;
                        prevY = y;
                        break;
                    }
                    case MotionEvent.ACTION_MOVE: {
                        float x = event.getX(), y = event.getY();
                        float deltaX = x - prevX, deltaY = y - prevY;
                        window.translationX += deltaX;
                        window.translationY += deltaY;
                        drawChessboardOnView();
                        drawBitmapOnView();
                        drawGridOnView();
                        if (transformeeBitmap != null) {
                            drawTransformeeOnViewBySelection();
                        } else if (llBehaviorText.getVisibility() == View.VISIBLE) {
                            drawTextOnView();
                        }
                        drawSelectionOnView();
                        prevX = x;
                        prevY = y;
                        break;
                    }
                    case MotionEvent.ACTION_UP:
                        tvStatus.setText("");
                        break;
                }
                break;
            }

            case 2: {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_MOVE: {
                        float x0 = event.getX(0), y0 = event.getY(0),
                                x1 = event.getX(1), y1 = event.getY(1);
                        double diagonal = Math.sqrt(Math.pow(x0 - x1, 2.0) + Math.pow(y0 - y1, 2.0));
                        double diagonalRatio = diagonal / prevDiagonal;
                        float scale = (float) (window.scale * diagonalRatio);
                        int scaledWidth = (int) (bitmap.getWidth() * scale), scaledHeight = (int) (bitmap.getHeight() * scale);
                        window.scale = scale;
                        imageWidth = scaledWidth;
                        imageHeight = scaledHeight;
                        float pivotX = (float) (this.pivotX * diagonalRatio), pivotY = (float) (this.pivotY * diagonalRatio);
                        window.translationX = window.translationX - pivotX + this.pivotX;
                        window.translationY = window.translationY - pivotY + this.pivotY;
                        drawChessboardOnView();
                        drawBitmapOnView();
                        drawGridOnView();
                        if (transformeeBitmap != null) {
                            drawTransformeeOnViewBySelection();
                        } else if (llBehaviorText.getVisibility() == View.VISIBLE) {
                            scaleTextSizeAndDrawTextOnView();
                        }
                        drawSelectionOnView();
                        this.pivotX = pivotX;
                        this.pivotY = pivotY;
                        prevDiagonal = diagonal;
                        break;
                    }
                    case MotionEvent.ACTION_POINTER_DOWN: {
                        float x0 = event.getX(0), y0 = event.getY(0),
                                x1 = event.getX(1), y1 = event.getY(1);
                        this.pivotX = (x0 + x1) / 2.0f - window.translationX;
                        this.pivotY = (y0 + y1) / 2.0f - window.translationY;
                        prevDiagonal = Math.sqrt(Math.pow(x0 - x1, 2.0) + Math.pow(y0 - y1, 2.0));
                        tvStatus.setText("");
                        break;
                    }
                    case MotionEvent.ACTION_POINTER_UP: {
                        float x = event.getX(1 - event.getActionIndex());
                        float y = event.getY(1 - event.getActionIndex());
                        tvStatus.setText(String.format("(%d, %d)",
                                toOriginal(x - window.translationX), toOriginal(y - window.translationY)));
                        prevX = event.getX(1 - event.getActionIndex());
                        prevY = event.getY(1 - event.getActionIndex());
                        break;
                    }
                }
                break;
            }
        }
        return true;
    };

    @SuppressLint("ClickableViewAccessibility")
    private final View.OnTouchListener onImageViewTouchWithSelectorListener = (v, event) -> {
        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN: {
                float x = event.getX(), y = event.getY();
                if (hasSelection && selectionStartX == selectionEndX && selectionStartY == selectionEndY) {
                    selectionEndX = toOriginal(x - window.translationX);
                    selectionEndY = toOriginal(y - window.translationY);
                } else {
                    hasSelection = true;
                    selectionStartX = toOriginal(x - window.translationX);
                    selectionStartY = toOriginal(y - window.translationY);
                    selectionEndX = selectionStartX;
                    selectionEndY = selectionStartY;
                }
                drawSelectionOnViewByStartsAndEnds();
                tvStatus.setText(String.format("Start: (%d, %d), End: (%d, %d), Area: 1 ?? 1",
                        selectionStartX, selectionStartY, selectionStartX, selectionStartY));
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                float x = event.getX(), y = event.getY();
                selectionEndX = toOriginal(x - window.translationX);
                selectionEndY = toOriginal(y - window.translationY);
                drawSelectionOnViewByStartsAndEnds();
                tvStatus.setText(String.format("Start: (%d, %d), End: (%d, %d), Area: %d ?? %d",
                        selectionStartX, selectionStartY, selectionEndX, selectionEndY,
                        Math.abs(selectionEndX - selectionStartX) + 1, Math.abs(selectionEndY - selectionStartY) + 1));
                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                optimizeSelection();
                drawSelectionOnView();
                tvStatus.setText(hasSelection
                        ? String.format("Start: (%d, %d), End: (%d, %d), Area: %d ?? %d",
                        selection.left, selection.top, selection.right, selection.bottom,
                        selection.right - selection.left + 1, selection.bottom - selection.top + 1)
                        : "");
                break;
        }
        return true;
    };

    @SuppressLint("ClickableViewAccessibility")
    View.OnTouchListener onImageViewTouchWithTextListener = (v, event) -> {
        switch (llBehaviorText.getVisibility()) {

            case View.VISIBLE: {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        prevX = event.getX() - toScaled(textX);
                        prevY = event.getY() - toScaled(textY);
                        drawTextOnView();
                        break;

                    case MotionEvent.ACTION_MOVE: {
                        float x = event.getX(), y = event.getY();
                        textX = toOriginal(x - prevX);
                        textY = toOriginal(y - prevY);
                        drawTextOnView();
                        break;
                    }
                }
                break;
            }

            case View.INVISIBLE:
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        textX = toOriginal(event.getX() - window.translationX);
                        textY = toOriginal(event.getY() - window.translationY);
                        llBehaviorText.setVisibility(View.VISIBLE);
                        scaleTextSizeAndDrawTextOnView();
                        prevX = window.translationX;
                        prevY = window.translationY;
                        break;
                }
        }
        return true;
    };

    @SuppressLint("ClickableViewAccessibility")
    View.OnTouchListener onImageViewTouchWithTransformerListener = (v, event) -> {
        if (!hasSelection) {
            return true;
        }

        switch (event.getPointerCount()) {

            case 1:
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        if (!hasSelection) {
                            break;
                        }
                        float x = event.getX(), y = event.getY();
                        int width = selection.right - selection.left + 1, height = selection.bottom - selection.top + 1;
                        if (width > 0 && height > 0) {
                            if (transformeeBitmap == null) {
                                transformeeTranslationX = window.translationX + toScaled(selection.left);
                                transformeeTranslationY = window.translationY + toScaled(selection.top);
                                transformeeBitmap = Bitmap.createBitmap(bitmap,
                                        selection.left, selection.top, width, height);
                                if (cbTransformerFill.isChecked()) {
                                    colorPaint.setColor(paint.getColor());
                                    canvas.drawRect(selection.left, selection.top, selection.right + 1, selection.bottom + 1, colorPaint);
                                } else {
                                    canvas.drawRect(selection.left, selection.top, selection.right + 1, selection.bottom + 1, eraser);
                                }
                                history.offer(bitmap);
                            }
                            drawBitmapOnView();
                            drawTransformeeAndSelectionOnViewByTranslation();
                        }
                        tvStatus.setText(String.format("Top-left: (%d, %d), Bottom-right: (%d, %d)",
                                selection.left, selection.top, selection.right, selection.bottom));
                        prevX = x;
                        prevY = y;
                        break;
                    }
                    case MotionEvent.ACTION_MOVE: {
                        if (transformeeBitmap == null) {
                            break;
                        }
                        float x = event.getX(), y = event.getY();
                        transformeeTranslationX += x - prevX;
                        transformeeTranslationY += y - prevY;
                        drawTransformeeAndSelectionOnViewByTranslation();
                        tvStatus.setText(String.format("Top-left: (%d, %d), Bottom-right: (%d, %d)",
                                selection.left, selection.top, selection.right, selection.bottom));
                        prevX = x;
                        prevY = y;
                        break;
                    }
                }
                break;

            case 2:
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_MOVE: {
                        float x0 = event.getX(0), y0 = event.getY(0),
                                x1 = event.getX(1), y1 = event.getY(1);
                        PositionsF scaledSelection = new PositionsF();
                        scaledSelection.left = window.translationX + toScaled(selection.left);
                        scaledSelection.top = window.translationY + toScaled(selection.top);
                        scaledSelection.right = window.translationX + toScaled(selection.right);
                        scaledSelection.bottom = window.translationY + toScaled(selection.bottom);
                        PositionsF dpb = new PositionsF();
                        dpb.left = Math.min(x0 - scaledSelection.left, x1 - scaledSelection.left);
                        dpb.top = Math.min(y0 - scaledSelection.top, y1 - scaledSelection.top);
                        dpb.right = Math.min(scaledSelection.right - x0, scaledSelection.right - x1);
                        dpb.bottom = Math.min(scaledSelection.bottom - y0, scaledSelection.bottom - y1);
                        if (cbTransformerLar.isChecked()) {
                            PositionsF dpbDiff = new PositionsF();
                            dpbDiff.left = transfromeeDpb.left - dpb.left;
                            dpbDiff.top = transfromeeDpb.top - dpb.top;
                            dpbDiff.right = transfromeeDpb.right - dpb.right;
                            dpbDiff.bottom = transfromeeDpb.bottom - dpb.bottom;
                            if (Math.abs(dpbDiff.left) + Math.abs(dpbDiff.right) >= Math.abs(dpbDiff.top) + Math.abs(dpbDiff.bottom)) {
                                selection.left -= toOriginal(transfromeeDpb.left - dpb.left);
                                selection.right += toOriginal(transfromeeDpb.right - dpb.right);
                                double width = selection.right - selection.left + 1, height = width / transformeeAspectRatio;
                                double centerVertical = (selection.top + selection.bottom + 1) / 2.0;
                                selection.top = (int) (centerVertical - height / 2.0);
                                selection.bottom = (int) (centerVertical + height / 2.0);
                                scaledSelection.top = window.translationY + toScaled(selection.top);
                                scaledSelection.bottom = window.translationY + toScaled(selection.bottom);
                                transfromeeDpb.top = Math.min(y0 - scaledSelection.top, y1 - scaledSelection.top);
                                transfromeeDpb.bottom = Math.min(scaledSelection.bottom - y0, scaledSelection.bottom - y1);
                            } else {
                                selection.top -= toOriginal(transfromeeDpb.top - dpb.top);
                                selection.bottom += toOriginal(transfromeeDpb.bottom - dpb.bottom);
                                double height = selection.bottom - selection.top + 1, width = height * transformeeAspectRatio;
                                double centerHorizontal = (selection.right + selection.left + 1) / 2.0;
                                selection.left = (int) (centerHorizontal - width / 2.0);
                                selection.right = (int) (centerHorizontal + width / 2.0);
                                scaledSelection.left = window.translationX + toScaled(selection.left);
                                scaledSelection.right = window.translationX + toScaled(selection.right);
                                transfromeeDpb.left = Math.min(x0 - scaledSelection.left, x1 - scaledSelection.left);
                                transfromeeDpb.right = Math.min(scaledSelection.right - x0, scaledSelection.right - x1);
                            }
                        } else {
                            selection.left -= toOriginal(transfromeeDpb.left - dpb.left);
                            selection.top -= toOriginal(transfromeeDpb.top - dpb.top);
                            selection.right += toOriginal(transfromeeDpb.right - dpb.right);
                            selection.bottom += toOriginal(transfromeeDpb.bottom - dpb.bottom);
                        }
                        drawSelectionOnView();
                        tvStatus.setText(String.format("Area: %d ?? %d",
                                selection.right - selection.left + 1, selection.bottom - selection.top + 1));
                        break;
                    }
                    case MotionEvent.ACTION_POINTER_DOWN: {
                        float x0 = event.getX(0), y0 = event.getY(0),
                                x1 = event.getX(1), y1 = event.getY(1);
                        PositionsF scaledSelection = new PositionsF();
                        scaledSelection.left = window.translationX + toScaled(selection.left);
                        scaledSelection.top = window.translationY + toScaled(selection.top);
                        scaledSelection.right = window.translationX + toScaled(selection.right);
                        scaledSelection.bottom = window.translationY + toScaled(selection.bottom);
                        transfromeeDpb.left = Math.min(x0 - scaledSelection.left, x1 - scaledSelection.left);
                        transfromeeDpb.top = Math.min(y0 - scaledSelection.top, y1 - scaledSelection.top);
                        transfromeeDpb.right = Math.min(scaledSelection.right - x0, scaledSelection.right - x1);
                        transfromeeDpb.bottom = Math.min(scaledSelection.bottom - y0, scaledSelection.bottom - y1);
                        if (cbTransformerLar.isChecked()) {
                            transformeeAspectRatio = (double) (selection.right - selection.left + 1) / (double) (selection.bottom - selection.top + 1);
                        }
                        tvStatus.setText(String.format("Area: %d ?? %d",
                                selection.right - selection.left + 1, selection.bottom - selection.top + 1));
                        break;
                    }
                    case MotionEvent.ACTION_POINTER_UP: {
                        int width = selection.right - selection.left + 1, height = selection.bottom - selection.top + 1;
                        if (width > 0 && height > 0) {
                            Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                            new Canvas(bm).drawBitmap(transformeeBitmap,
                                    new Rect(0, 0, transformeeBitmap.getWidth(), transformeeBitmap.getHeight()),
                                    new Rect(0, 0, width, height),
                                    opaquePaint);
                            transformeeBitmap.recycle();
                            transformeeBitmap = bm;
                            transformeeTranslationX = window.translationX + toScaled(selection.left);
                            transformeeTranslationY = window.translationY + toScaled(selection.top);
                        } else if (transformeeBitmap != null) {
                            transformeeBitmap.recycle();
                            transformeeBitmap = null;
                        }
                        drawTransformeeAndSelectionOnViewByTranslation();
                        prevX = event.getX(1 - event.getActionIndex());
                        prevY = event.getY(1 - event.getActionIndex());
                        tvStatus.setText("");
                        break;
                    }
                }
                break;
        }
        return true;
    };

    @SuppressLint("ClickableViewAccessibility")
    private final CompoundButton.OnCheckedChangeListener onScalerCheckBoxCheckedChangeListener = (buttonView, isChecked) -> {
        if (isChecked) {
            flImageView.setOnTouchListener(onImageViewTouchWithScalerListener);
        } else {
            flImageView.setOnTouchListener((View.OnTouchListener) cbScaler.getTag());
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    private final CompoundButton.OnCheckedChangeListener onTransformerRadioButtonCheckedChangeListener = (buttonView, isChecked) -> {
        if (isChecked) {
            cbScaler.setChecked(false);
            cbScaler.setTag(onImageViewTouchWithTransformerListener);
            flImageView.setOnTouchListener(onImageViewTouchWithTransformerListener);
            llBehaviorTransformer.setVisibility(View.VISIBLE);
        } else {
            drawTransformeeOnCanvas();
            llBehaviorTransformer.setVisibility(View.GONE);
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    private final CompoundButton.OnCheckedChangeListener onTextRadioButtonCheckedChangeListener = (buttonView, isChecked) -> {
        if (isChecked) {
            cbScaler.setChecked(false);
            cbScaler.setTag(onImageViewTouchWithTextListener);
            flImageView.setOnTouchListener(onImageViewTouchWithTextListener);
        } else {
            drawTextOnCanvas();
        }
    };

    private void addBitmap(Bitmap bitmap, int width, int height) {
        addBitmap(bitmap,
                width, height,
                null, getString(R.string.untitled), null);
    }

    private void addBitmap(Bitmap bitmap,
                           int width, int height,
                           String path, String title, Bitmap.CompressFormat compressFormat) {
        window = new Window();
        windows.add(window);
        window.bitmap = bitmap;
        currentBitmapIndex = windows.size() - 1;
        history = new BitmapHistory();
        window.history = history;
        history.offer(bitmap);
        window.path = path;
        window.compressFormat = compressFormat;
        cellGrid = new CellGrid();
        window.cellGrid = cellGrid;

        window.scale = (float) ((double) viewWidth / (double) width);
        imageWidth = (int) toScaled(width);
        imageHeight = (int) toScaled(height);
        window.translationX = 0.0f;
        window.translationY = 0.0f;

        recycleBitmapIfIsNotNull(transformeeBitmap);
        transformeeBitmap = null;
        hasSelection = false;

        drawChessboardOnView();
        drawBitmapOnView();
        drawGridOnView();
        drawSelectionOnView();

        tabLayout.addTab(tabLayout.newTab().setText(title).setTag(bitmap));
        tabLayout.getTabAt(currentBitmapIndex).select();
    }

    private void bucketFill(int x, int y, int color) {
        int pixel = bitmap.getPixel(x, y);
        if (pixel == color) {
            return;
        }
        int left, top, right, bottom;
        if (hasSelection) {
            left = selection.left;
            top = selection.top;
            right = selection.right;
            bottom = selection.bottom;
        } else {
            left = 0;
            top = 0;
            right = bitmap.getWidth() - 1;
            bottom = bitmap.getHeight() - 1;
        }
        for (y = top; y <= bottom; ++y) {
            for (x = left; x <= right; ++x) {
                if (bitmap.getPixel(x, y) == pixel) {
                    bitmap.setPixel(x, y, color);
                }
            }
        }
        drawBitmapOnView();
        history.offer(bitmap);
    }

    private void clearCanvas(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }

    private void clearCanvasAndInvalidateView(Canvas canvas, ImageView imageView) {
        clearCanvas(canvas);
        imageView.invalidate();
    }

    private void createGraphic(int width, int height) {
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        addBitmap(bitmap, width, height);
    }

    private void drawBitmapOnCanvas(Bitmap bm, float translX, float translY, Canvas cv) {
        int startX = translX >= 0.0f ? 0 : toOriginal(-translX);
        int startY = translY >= 0.0f ? 0 : toOriginal(-translY);
        int bitmapWidth = bm.getWidth(), bitmapHeight = bm.getHeight();
        int scaledBmpWidth = (int) toScaled(bitmapWidth), scaledBmpHeight = (int) toScaled(bitmapHeight);
        int endX = Math.min(toOriginal(translX + scaledBmpWidth <= viewWidth ? scaledBmpWidth : viewWidth - translX) + 1, bitmapWidth);
        int endY = Math.min(toOriginal(translY + scaledBmpHeight <= viewHeight ? scaledBmpHeight : viewHeight - translY) + 1, bitmapHeight);
        float left = translX >= 0.0f ? translX : translX % window.scale;
        float top = translY >= 0.0f ? translY : translY % window.scale;
        if (isScaledMuch()) {
            float t = top, b = t + window.scale;
            for (int y = startY; y < endY; ++y, t += window.scale, b += window.scale) {
                float l = left;
                for (int x = startX; x < endX; ++x) {
                    colorPaint.setColor(bm.getPixel(x, y));
                    cv.drawRect(l, t, l += window.scale, b, colorPaint);
                }
            }
        } else {
            float right = Math.min(translX + scaledBmpWidth, viewWidth);
            float bottom = Math.min(translY + scaledBmpHeight, viewHeight);
            cv.drawBitmap(bm,
                    new Rect(startX, startY, endX, endY),
                    new RectF(left, top, right, bottom),
                    opaquePaint);
        }
    }

    private void drawBitmapOnView() {
        clearCanvas(viewCanvas);
        drawBitmapOnCanvas(bitmap, window.translationX, window.translationY, viewCanvas);
        imageView.invalidate();
    }

    private void drawChessboardOnView() {
        clearCanvas(chessboardCanvas);
        float left = Math.max(0.0f, window.translationX);
        float top = Math.max(0.0f, window.translationY);
        float right = Math.min(window.translationX + imageWidth, viewWidth);
        float bottom = Math.min(window.translationY + imageHeight, viewHeight);

        chessboardCanvas.drawLine(left, top, left - 100.0f, top, gridPaint);
        chessboardCanvas.drawLine(left, top, left, top - 100.0f, gridPaint);
        chessboardCanvas.drawLine(right, top, right + 100.0f, top, gridPaint);
        chessboardCanvas.drawLine(right, top, right, top - 100.0f, gridPaint);
        chessboardCanvas.drawLine(left, bottom, left - 100.0f, bottom, gridPaint);
        chessboardCanvas.drawLine(left, bottom, left, bottom + 100.0f, gridPaint);
        chessboardCanvas.drawLine(right, bottom, right + 100.0f, bottom, gridPaint);
        chessboardCanvas.drawLine(right, bottom, right, bottom + 100.0f, gridPaint);

        chessboardCanvas.drawBitmap(chessboard,
                new Rect((int) left, (int) top, (int) right, (int) bottom),
                new RectF(left, top, right, bottom),
                opaquePaint);
        ivChessboard.invalidate();
    }

    private void drawGridOnView() {
        clearCanvas(gridCanvas);
        float startX = window.translationX >= 0.0f ? window.translationX : window.translationX % window.scale,
                startY = window.translationY >= 0.0f ? window.translationY : window.translationY % window.scale,
                endX = Math.min(window.translationX + imageWidth, viewWidth),
                endY = Math.min(window.translationY + imageHeight, viewHeight);
        if (isScaledMuch()) {
            for (float x = startX; x < endX; x += window.scale) {
                gridCanvas.drawLine(x, startY, x, endY, gridPaint);
            }
            for (float y = startY; y < endY; y += window.scale) {
                gridCanvas.drawLine(startX, y, endX, y, gridPaint);
            }
        }
        if (cellGrid.enabled) {
            if (cellGrid.sizeX > 1) {
                float scaledSizeX = toScaled(cellGrid.sizeX),
                        scaledSpacingX = toScaled(cellGrid.spacingX);
                startX = (window.translationX >= 0.0f ? window.translationX : window.translationX % (scaledSizeX + scaledSpacingX)) + toScaled(cellGrid.offsetX);
                startY = Math.max(0.0f, window.translationY);
                if (cellGrid.spacingX <= 0) {
                    float x = startX;
                    while (x < endX) {
                        gridCanvas.drawLine(x, startY, x, endY, cellGridPaint);
                        x += scaledSizeX;
                    }
                } else {
                    float x = startX;
                    while (true) {
                        gridCanvas.drawLine(x, startY, x, endY, cellGridPaint);
                        if ((x += scaledSizeX) >= endX) {
                            break;
                        }
                        gridCanvas.drawLine(x, startY, x, endY, cellGridPaint);
                        if ((x += scaledSpacingX) >= endX) {
                            break;
                        }
                    }
                }
            }
            if (cellGrid.sizeY > 1) {
                float scaledSizeY = toScaled(cellGrid.sizeY),
                        scaledSpacingY = toScaled(cellGrid.spacingY);
                startY = (window.translationY >= 0.0f ? window.translationY : window.translationY % (scaledSizeY + scaledSpacingY)) + toScaled(cellGrid.offsetY);
                startX = Math.max(0.0f, window.translationX);
                if (cellGrid.spacingY <= 0) {
                    float y = startY;
                    while (y < endY) {
                        gridCanvas.drawLine(startX, y, endX, y, cellGridPaint);
                        y += scaledSizeY;
                    }
                } else {
                    float y = startY;
                    while (true) {
                        gridCanvas.drawLine(startX, y, endX, y, cellGridPaint);
                        if ((y += scaledSizeY) >= endY) {
                            break;
                        }
                        gridCanvas.drawLine(startX, y, endX, y, cellGridPaint);
                        if ((y += scaledSpacingY) >= endY) {
                            break;
                        }
                    }
                }
            }
        }
        ivGrid.invalidate();
    }

    private void drawLineOnCanvas(int startX, int startY, int stopX, int stopY, Paint paint) {
        if (startX <= stopX) ++stopX;
        else ++startX;
        if (startY <= stopY) ++stopY;
        else ++startY;

        canvas.drawLine(startX, startY, stopX, stopY, paint);
    }

    private void drawPoint(Canvas canvas, float x, float y, String text) {
        canvas.drawLine(x - 100.0f, y, x + 100.0f, y, pointPaint);
        canvas.drawLine(x, y - 100.0f, x, y + 100.0f, pointPaint);
        canvas.drawText(text, x, y, pointPaint);
        imageView.invalidate();
    }

    private void drawRectOnCanvas(int left, int top, int right, int bottom) {
        if (left <= right) ++right;
        else ++left;
        if (top <= bottom) ++bottom;
        else ++top;

        canvas.drawRect(left, top, right, bottom, paint);
    }

    private void drawRectOnView(int left, int top, int right, int bottom) {
        clearCanvas(previewCanvas);

        if (left <= right) ++right;
        else ++left;
        if (top <= bottom) ++bottom;
        else ++top;

        previewCanvas.drawRect(
                window.translationX + toScaled(left),
                window.translationY + toScaled(top),
                window.translationX + toScaled(right),
                window.translationY + toScaled(bottom),
                paint);
        ivPreview.invalidate();
    }

    private void drawSelectionOnView() {
        clearCanvas(selectionCanvas);
        if (hasSelection) {
            selectionCanvas.drawRect(
                    window.translationX + toScaled(selection.left),
                    window.translationY + toScaled(selection.top),
                    window.translationX + toScaled(selection.right + 1),
                    window.translationY + toScaled(selection.bottom + 1),
                    selector);
        }
        ivSelection.invalidate();
    }

    private void drawSelectionOnViewByStartsAndEnds() {
        clearCanvas(selectionCanvas);
        if (hasSelection) {
            if (selectionStartX <= selectionEndX) {
                selection.left = selectionStartX;
                selection.right = selectionEndX;
            } else {
                selection.left = selectionEndX;
                selection.right = selectionStartX;
            }
            if (selectionStartY <= selectionEndY) {
                selection.top = selectionStartY;
                selection.bottom = selectionEndY;
            } else {
                selection.top = selectionEndY;
                selection.bottom = selectionStartY;
            }
            selectionCanvas.drawRect(
                    window.translationX + toScaled(selection.left),
                    window.translationY + toScaled(selection.top),
                    window.translationX + toScaled(selection.right + 1),
                    window.translationY + toScaled(selection.bottom + 1),
                    selector);
        }
        ivSelection.invalidate();
    }

    private void drawTextOnCanvas() {
        if (llBehaviorText.getVisibility() != View.VISIBLE) {
            return;
        }
        try {
            paint.setTextSize(Float.parseFloat(etTextSize.getText().toString()));
        } catch (NumberFormatException e) {
        }
        canvas.drawText(etText.getText().toString(), textX, textY, paint);
        drawBitmapOnView();
        clearCanvasAndInvalidateView(previewCanvas, ivPreview);
        inputMethodManager.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
        llBehaviorText.setVisibility(View.INVISIBLE);
        history.offer(bitmap);
    }

    private void drawTextOnView() {
        clearCanvas(previewCanvas);
        previewCanvas.drawText(etText.getText().toString(), window.translationX + toScaled(textX), window.translationY + toScaled(textY), paint);
        ivPreview.invalidate();
    }

    private void drawTransformeeOnCanvas() {
        if (transformeeBitmap != null) {
            if (hasSelection) {
                canvas.drawBitmap(transformeeBitmap, selection.left, selection.top, paint);
                optimizeSelection();
                drawSelectionOnView();
                drawBitmapOnView();
                history.offer(bitmap);
                tvStatus.setText("");
            }
            transformeeBitmap.recycle();
            transformeeBitmap = null;
        }
        clearCanvasAndInvalidateView(previewCanvas, ivPreview);
    }

    private void drawTransformeeAndSelectionOnViewByTranslation() {
        clearCanvas(previewCanvas);
        if (hasSelection && transformeeBitmap != null) {
            selection.left = toOriginal(transformeeTranslationX - window.translationX);
            selection.top = toOriginal(transformeeTranslationY - window.translationY);
            selection.right = selection.left + transformeeBitmap.getWidth() - 1;
            selection.bottom = selection.top + transformeeBitmap.getHeight() - 1;
            float ttx = toScaled(selection.left) + window.translationX;
            float tty = toScaled(selection.top) + window.translationY;
            drawBitmapOnCanvas(transformeeBitmap, ttx, tty, previewCanvas);
        }
        ivPreview.invalidate();
        drawSelectionOnView();
    }

    private void drawTransformeeOnViewBySelection() {
        clearCanvas(previewCanvas);
        if (hasSelection && transformeeBitmap != null) {
            float ttx = toScaled(selection.left) + window.translationX;
            float tty = toScaled(selection.top) + window.translationY;
            drawBitmapOnCanvas(transformeeBitmap, ttx, tty, previewCanvas);
            transformeeTranslationX = ttx;
            transformeeTranslationY = tty;
        }
        ivPreview.invalidate();
    }

    private void floodFill(int x, int y, int color) {
        new FloodFill(x, y, color).start();
    }

    private boolean isScaledMuch() {
        return window.scale >= 16.0f;
    }

    private void load() {
        viewWidth = imageView.getWidth();
        viewHeight = imageView.getHeight();

        window = new Window();
        windows.add(window);
        bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        window.bitmap = bitmap;
        currentBitmapIndex = 0;
        canvas = new Canvas(bitmap);
        history = new BitmapHistory();
        window.history = history;
        history.offer(bitmap);
        window.path = null;
        cellGrid = new CellGrid();
        window.cellGrid = cellGrid;

        window.scale = 20.0f;
        imageWidth = 960;
        imageHeight = 960;
        window.translationX = 0.0f;
        window.translationY = 0.0f;

        viewBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
        viewCanvas = new Canvas(viewBitmap);
        imageView.setImageBitmap(viewBitmap);

        gridBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_4444);
        gridCanvas = new Canvas(gridBitmap);
        ivGrid.setImageBitmap(gridBitmap);
        drawGridOnView();

        chessboardBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_4444);
        chessboardCanvas = new Canvas(chessboardBitmap);
        ivChessboard.setImageBitmap(chessboardBitmap);
        drawChessboardOnView();

        previewBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_4444);
        previewCanvas = new Canvas(previewBitmap);
        ivPreview.setImageBitmap(previewBitmap);

        selectionBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_4444);
        selectionCanvas = new Canvas(selectionBitmap);
        ivSelection.setImageBitmap(selectionBitmap);
        drawSelectionOnView();

        tabLayout.addTab(tabLayout.newTab().setText(R.string.untitled).setTag(bitmap));

        etEraserStrokeWidth.setText(String.valueOf(eraser.getStrokeWidth()));
        etPencilStrokeWidth.setText(String.valueOf(paint.getStrokeWidth()));
        etTextSize.setText(String.valueOf(paint.getTextSize()));
    }

    private void onChannelChanged(String hex, SeekBar seekBar) {
        try {
            seekBar.setProgress(Integer.parseUnsignedInt(hex, 16));
        } catch (NumberFormatException e) {
        }
        int color = Color.argb(
                sbAlpha.getProgress(),
                sbRed.getProgress(),
                sbGreen.getProgress(),
                sbBlue.getProgress());
        paint.setColor(color);
        rbColor.setTextColor(color);
        if (llBehaviorText.getVisibility() == View.VISIBLE) {
            drawTextOnView();
        }
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cbBucketFillContiguous = findViewById(R.id.cb_bucket_fill_contiguous);
        cbScaler = findViewById(R.id.cb_scaler);
        cbTransformerFill = findViewById(R.id.cb_transformer_fill);
        cbTransformerLar = findViewById(R.id.cb_transformer_lar);
        etAlpha = findViewById(R.id.et_alpha);
        etBlue = findViewById(R.id.et_blue);
        etEraserStrokeWidth = findViewById(R.id.et_eraser_stroke_width);
        etGreen = findViewById(R.id.et_green);
        etPencilStrokeWidth = findViewById(R.id.et_pencil_stroke_width);
        etRed = findViewById(R.id.et_red);
        etText = findViewById(R.id.et_text);
        etTextSize = findViewById(R.id.et_text_size);
        flImageView = findViewById(R.id.fl_iv);
        imageView = findViewById(R.id.iv);
        inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        ivChessboard = findViewById(R.id.iv_chessboard);
        ivGrid = findViewById(R.id.iv_grid);
        ivPreview = findViewById(R.id.iv_preview);
        ivSelection = findViewById(R.id.iv_selection);
        llBehaviorBucketFill = findViewById(R.id.ll_behavior_bucket_fill);
        llBehaviorEraser = findViewById(R.id.ll_behavior_eraser);
        llBehaviorPencil = findViewById(R.id.ll_behavior_pencil);
        llBehaviorText = findViewById(R.id.ll_behavior_text);
        llBehaviorTransformer = findViewById(R.id.ll_behavior_transformer);
        rbBackgroundColor = findViewById(R.id.rb_background_color);
        rbForegroundColor = findViewById(R.id.rb_foreground_color);
        rbColor = rbForegroundColor;
        RadioButton rbPencil = findViewById(R.id.rb_pencil);
        rbTransformer = findViewById(R.id.rb_transformer);
        sbAlpha = findViewById(R.id.sb_alpha);
        sbBlue = findViewById(R.id.sb_blue);
        sbGreen = findViewById(R.id.sb_green);
        sbRed = findViewById(R.id.sb_red);
        tabLayout = findViewById(R.id.tl);
        tvStatus = findViewById(R.id.tv_status);

        onPropSizeXTextChangedListener = s -> {
            try {
                int i = Integer.parseUnsignedInt(s);
                etPropSizeY.removeTextChangedListener(onPropSizeYTextChangedListener);
                etPropSizeY.setText(String.valueOf(i * bitmap.getHeight() / bitmap.getWidth()));
                etPropSizeY.addTextChangedListener(onPropSizeYTextChangedListener);
            } catch (NumberFormatException e) {
            }
        };

        onPropSizeYTextChangedListener = s -> {
            try {
                int i = Integer.parseUnsignedInt(s);
                etPropSizeX.removeTextChangedListener(onPropSizeXTextChangedListener);
                etPropSizeX.setText(String.valueOf(i * bitmap.getWidth() / bitmap.getHeight()));
                etPropSizeX.addTextChangedListener(onPropSizeXTextChangedListener);
            } catch (NumberFormatException e) {
            }
        };

        findViewById(R.id.b_text_draw).setOnClickListener(v -> drawTextOnCanvas());
        cbScaler.setOnCheckedChangeListener(onScalerCheckBoxCheckedChangeListener);
        cbScaler.setTag(onImageViewTouchWithPencilListener);
        etAlpha.addTextChangedListener((AfterTextChangedListener) s -> onChannelChanged(s, sbAlpha));
        etBlue.addTextChangedListener((AfterTextChangedListener) s -> onChannelChanged(s, sbBlue));
        etGreen.addTextChangedListener((AfterTextChangedListener) s -> onChannelChanged(s, sbGreen));
        etRed.addTextChangedListener((AfterTextChangedListener) s -> onChannelChanged(s, sbRed));
        etText.addTextChangedListener((AfterTextChangedListener) s -> drawTextOnView());
        etTextSize.addTextChangedListener((AfterTextChangedListener) s -> scaleTextSizeAndDrawTextOnView());
        flImageView.setOnTouchListener(onImageViewTouchWithPencilListener);
        rbBackgroundColor.setOnCheckedChangeListener(onBackgroundColorRadioButtonCheckedChangeListener);
        rbForegroundColor.setOnCheckedChangeListener(onForegroundColorRadioButtonCheckedChangeListener);
        ((RadioButton) findViewById(R.id.rb_bucket_fill)).setOnCheckedChangeListener((buttonView, isChecked) -> onToolChange(buttonView, isChecked, onImageViewTouchWithBucketListener, llBehaviorBucketFill));
        ((RadioButton) findViewById(R.id.rb_eraser)).setOnCheckedChangeListener((buttonView, isChecked) -> onToolChange(buttonView, isChecked, onImageViewTouchWithEraserListener, llBehaviorEraser));
        ((RadioButton) findViewById(R.id.rb_eyedropper)).setOnCheckedChangeListener((buttonView, isChecked) -> onToolChange(buttonView, isChecked, onImageViewTouchWithEyedropperListener, null));
        rbPencil.setOnCheckedChangeListener((buttonView, isChecked) -> onToolChange(buttonView, isChecked, onImageViewTouchWithPencilListener, llBehaviorPencil));
        ((RadioButton) findViewById(R.id.rb_rectangle)).setOnCheckedChangeListener((buttonView, isChecked) -> onToolChange(buttonView, isChecked, onImageViewTouchWithRectListener, null));
        ((RadioButton) findViewById(R.id.rb_selector)).setOnCheckedChangeListener((buttonView, isChecked) -> onToolChange(buttonView, isChecked, onImageViewTouchWithSelectorListener, null));
        ((RadioButton) findViewById(R.id.rb_text)).setOnCheckedChangeListener(onTextRadioButtonCheckedChangeListener);
        rbTransformer.setOnCheckedChangeListener(onTransformerRadioButtonCheckedChangeListener);
        sbAlpha.setOnSeekBarChangeListener((OnProgressChangeListener) progress -> etAlpha.setText(String.format(FORMAT_02X, progress)));
        sbBlue.setOnSeekBarChangeListener((OnProgressChangeListener) progress -> etBlue.setText(String.format(FORMAT_02X, progress)));
        sbGreen.setOnSeekBarChangeListener((OnProgressChangeListener) progress -> etGreen.setText(String.format(FORMAT_02X, progress)));
        sbRed.setOnSeekBarChangeListener((OnProgressChangeListener) progress -> etRed.setText(String.format(FORMAT_02X, progress)));
        tabLayout.addOnTabSelectedListener(onTabSelectedListener);

        etEraserStrokeWidth.addTextChangedListener((AfterTextChangedListener) s -> {
            try {
                eraser.setStrokeWidth(Float.parseFloat(s));
            } catch (NumberFormatException e) {
            }
        });

        etPencilStrokeWidth.addTextChangedListener((AfterTextChangedListener) s -> {
            try {
                paint.setStrokeWidth(Float.parseFloat(s));
            } catch (NumberFormatException e) {
            }
        });

        chessboard = BitmapFactory.decodeResource(getResources(), R.mipmap.chessboard);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    protected void onDestroy() {

        canvas = null;
        bitmap.recycle();
        bitmap = null;

        viewCanvas = null;
        viewBitmap.recycle();
        viewBitmap = null;

        gridCanvas = null;
        gridBitmap.recycle();
        gridBitmap = null;

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void onToolChange(CompoundButton buttonView, boolean isChecked, View.OnTouchListener onImageViewTouchListener, View toolBehavior) {
        if (isChecked) {
            cbScaler.setChecked(false);
            cbScaler.setTag(onImageViewTouchListener);
            flImageView.setOnTouchListener(onImageViewTouchListener);
        }
        if (toolBehavior != null) {
            toolBehavior.setVisibility(isChecked ? View.VISIBLE : View.INVISIBLE);
        }
    }

    @Override
    @SuppressLint("NonConstantResourceId")
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.i_cell_grid:
                AlertDialog cellGridDialog = new AlertDialog.Builder(this)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok, onCellGridDialogPosButtonClickListener)
                        .setTitle(R.string.cell_grid)
                        .setView(R.layout.cell_grid)
                        .show();

                cbCellGridEnabled = cellGridDialog.findViewById(R.id.cb_cg_enabled);
                etCellGridSizeX = cellGridDialog.findViewById(R.id.et_cg_size_x);
                etCellGridSizeY = cellGridDialog.findViewById(R.id.et_cg_size_y);
                etCellGridSpacingX = cellGridDialog.findViewById(R.id.et_cg_spacing_x);
                etCellGridSpacingY = cellGridDialog.findViewById(R.id.et_cg_spacing_y);
                etCellGridOffsetX = cellGridDialog.findViewById(R.id.et_cg_offset_x);
                etCellGridOffsetY = cellGridDialog.findViewById(R.id.et_cg_offset_y);

                cbCellGridEnabled.setChecked(cellGrid.enabled);
                etCellGridSizeX.setText(String.valueOf(cellGrid.sizeX));
                etCellGridSizeY.setText(String.valueOf(cellGrid.sizeY));
                etCellGridSpacingX.setText(String.valueOf(cellGrid.spacingX));
                etCellGridSpacingY.setText(String.valueOf(cellGrid.spacingY));
                etCellGridOffsetX.setText(String.valueOf(cellGrid.offsetX));
                etCellGridOffsetY.setText(String.valueOf(cellGrid.offsetY));
                break;

            case R.id.i_close:
                if (windows.size() == 1) {
                    break;
                }
                recycleBitmapIfIsNotNull(transformeeBitmap);
                transformeeBitmap = null;
                windows.remove(currentBitmapIndex);
                tabLayout.removeTabAt(currentBitmapIndex);
                break;

            case R.id.i_copy:
                if (!hasSelection) {
                    break;
                }
                if (transformeeBitmap == null) {
                    if (clipboard != null) {
                        clipboard.recycle();
                    }
                    clipboard = Bitmap.createBitmap(bitmap, selection.left, selection.top,
                            selection.right - selection.left + 1, selection.bottom - selection.top + 1);
                } else {
                    clipboard = Bitmap.createBitmap(transformeeBitmap);
                }
                break;

            case R.id.i_crop: {
                if (!hasSelection) {
                    break;
                }
                drawTransformeeOnCanvas();
                drawTextOnCanvas();
                int width = selection.right - selection.left + 1, height = selection.bottom - selection.top + 1;
                Bitmap bm = Bitmap.createBitmap(bitmap, selection.left, selection.top, width, height);
                resizeBitmap(width, height, false);
                canvas.drawBitmap(bm, 0.0f, 0.0f, opaquePaint);
                drawBitmapOnView();
                history.offer(bitmap);
                break;
            }

            case R.id.i_cut:
                if (!hasSelection) {
                    break;
                }
                if (transformeeBitmap == null) {
                    if (clipboard != null) {
                        clipboard.recycle();
                    }
                    clipboard = Bitmap.createBitmap(bitmap, selection.left, selection.top,
                            selection.right - selection.left + 1, selection.bottom - selection.top + 1);
                    canvas.drawRect(selection.left, selection.top, selection.right + 1, selection.bottom + 1, eraser);
                    drawBitmapOnView();
                    history.offer(bitmap);
                } else {
                    clipboard = Bitmap.createBitmap(transformeeBitmap);
                    transformeeBitmap.recycle();
                    transformeeBitmap = null;
                    clearCanvasAndInvalidateView(previewCanvas, ivPreview);
                }
                break;

            case R.id.i_delete:
                if (!hasSelection) {
                    break;
                }
                if (transformeeBitmap == null) {
                    canvas.drawRect(selection.left, selection.top, selection.right + 1, selection.bottom + 1, eraser);
                    drawBitmapOnView();
                    history.offer(bitmap);
                } else {
                    transformeeBitmap.recycle();
                    transformeeBitmap = null;
                    clearCanvasAndInvalidateView(previewCanvas, ivPreview);
                }
                break;

            case R.id.i_new:
                drawTransformeeOnCanvas();
                drawTextOnCanvas();

                AlertDialog newGraphicDialog = new AlertDialog.Builder(this)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok, onNewGraphicDialogPosButtonClickListener)
                        .setTitle(R.string.new_)
                        .setView(R.layout.new_graphic)
                        .show();

                etNewGraphicSizeX = newGraphicDialog.findViewById(R.id.et_new_size_x);
                etNewGraphicSizeY = newGraphicDialog.findViewById(R.id.et_new_size_y);
                break;

            case R.id.i_open:
                drawTransformeeOnCanvas();
                drawTextOnCanvas();

                getImage.launch("image/*");
                break;

            case R.id.i_paste:
                if (clipboard == null) {
                    break;
                }
                drawTransformeeOnCanvas();
                drawTextOnCanvas();

                transformeeBitmap = Bitmap.createBitmap(clipboard);
                selection.left = window.translationX >= 0.0f ? 0 : toOriginal(-window.translationX) + 1;
                selection.top = window.translationY >= 0.0f ? 0 : toOriginal(-window.translationY) + 1;
                selection.right = selection.left + Math.min(transformeeBitmap.getWidth(), bitmap.getWidth());
                selection.bottom = selection.top + Math.min(transformeeBitmap.getHeight(), bitmap.getHeight());
                transformeeTranslationX = window.translationX + toScaled(selection.left);
                transformeeTranslationY = window.translationY + toScaled(selection.top);
                hasSelection = true;
                rbTransformer.setChecked(true);
                drawTransformeeAndSelectionOnViewByTranslation();
                break;

            case R.id.i_properties:
                drawTransformeeOnCanvas();
                drawTextOnCanvas();

                AlertDialog propertiesDialog = new AlertDialog.Builder(this)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok, onPropDialogPosButtonClickListener)
                        .setTitle(R.string.properties)
                        .setView(R.layout.properties)
                        .show();

                cbPropLar = propertiesDialog.findViewById(R.id.cb_prop_lar);
                etPropSizeX = propertiesDialog.findViewById(R.id.et_prop_size_x);
                etPropSizeY = propertiesDialog.findViewById(R.id.et_prop_size_y);
                rbPropStretch = propertiesDialog.findViewById(R.id.rb_prop_stretch);
                rbPropCrop = propertiesDialog.findViewById(R.id.rb_prop_crop);

                cbPropLar.setOnCheckedChangeListener(onPropLarCheckBoxCheckedChangeListener);
                etPropSizeX.setText(String.valueOf(bitmap.getWidth()));
                etPropSizeY.setText(String.valueOf(bitmap.getHeight()));
                rbPropStretch.setChecked(true);
                break;

            case R.id.i_redo: {
                if (history.canRedo()) {
                    undoOrRedo(history.redo());
                }
                break;
            }

            case R.id.i_save:
                save();
                break;

            case R.id.i_save_as:
                saveAs();
                break;

            case R.id.i_select_all:
                selection.left = 0;
                selection.top = 0;
                selection.right = bitmap.getWidth() - 1;
                selection.bottom = bitmap.getHeight() - 1;
                hasSelection = true;
                drawSelectionOnView();
                selectionStartX = selection.left;
                selectionStartY = selection.top;
                selectionEndX = selection.right;
                selectionEndY = selection.bottom;
                break;

            case R.id.i_undo: {
                if (history.canUndo()) {
                    undoOrRedo(history.undo());
                }
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void recycleBitmapIfIsNotNull(Bitmap bm) {
        if (bm != null) {
            bm.recycle();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasNotLoaded && hasFocus) {
            hasNotLoaded = false;
            load();
        }
    }

    private void openFile(Bitmap bm, Uri uri) {
        int width = bm.getWidth(), height = bm.getHeight();
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        canvas.drawBitmap(bm, 0.0f, 0.0f, paint);
        bm.recycle();
        DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri);
        String path = null;
        Bitmap.CompressFormat compressFormat = null;
        switch (documentFile.getType()) {
            case "image/jpeg":
                compressFormat = Bitmap.CompressFormat.JPEG;
                path = UriToPathUtil.getRealFilePath(this, uri);
                break;
            case "image/png":
                compressFormat = Bitmap.CompressFormat.PNG;
                path = UriToPathUtil.getRealFilePath(this, uri);
                break;
            default:
                Toast.makeText(this, R.string.not_supported_file_type, Toast.LENGTH_SHORT).show();
                break;
        }
        addBitmap(bitmap,
                width, height,
                path, documentFile.getName(), compressFormat);
    }

    private void optimizeSelection() {
        int bitmapWidth = bitmap.getWidth(), bitmapHeight = bitmap.getHeight();
        if (selection.left < bitmapWidth && selection.top < bitmapHeight
                && selection.right >= 0 && selection.bottom >= 0) {
            selection.left = Math.max(0, selection.left);
            selection.top = Math.max(0, selection.top);
            selection.right = Math.min(bitmapWidth - 1, selection.right);
            selection.bottom = Math.min(bitmapHeight - 1, selection.bottom);
        } else {
            hasSelection = false;
        }
    }

    private void resizeBitmap(int width, int height, boolean stretch) {
        Bitmap bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        if (stretch) {
            cv.drawBitmap(bitmap,
                    new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                    new RectF(0.0f, 0.0f, width, height),
                    opaquePaint);
        } else {
            cv.drawBitmap(bitmap, 0.0f, 0.0f, opaquePaint);
        }
        bitmap.recycle();
        bitmap = bm;
        window.bitmap = bitmap;
        canvas = cv;
        imageWidth = (int) toScaled(width);
        imageHeight = (int) toScaled(height);

        recycleBitmapIfIsNotNull(transformeeBitmap);
        transformeeBitmap = null;
        hasSelection = false;

        drawChessboardOnView();
        drawGridOnView();
        drawSelectionOnView();
    }

    private void save() {
        save(window.path);
    }

    private void save(String path) {
        if (path == null) {
            getTree.launch(null);
            return;
        }

        drawTransformeeOnCanvas();
        drawTextOnCanvas();

        File file = new File(path);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(window.compressFormat, 100, fos);
            fos.flush();
        } catch (IOException e) {
            Toast.makeText(this, "Failed\n" + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(Uri.fromFile(file));
        sendBroadcast(intent);
    }

    private void saveAs() {
        String path = null;
        getTree.launch(null);
    }

    private void scaleTextSizeAndDrawTextOnView() {
        try {
            paint.setTextSize(toScaled((int) Float.parseFloat(etTextSize.getText().toString())));
        } catch (NumberFormatException e) {
        }
        drawTextOnView();
    }

    private void showPaintColorOnSeekBars() {
        int color = paint.getColor();
        int red = Color.red(color),
                green = Color.green(color),
                blue = Color.blue(color),
                alpha = Color.alpha(color);

        sbRed.setProgress(red);
        etRed.setText(String.format(FORMAT_02X, red));

        sbGreen.setProgress(green);
        etGreen.setText(String.format(FORMAT_02X, green));

        sbBlue.setProgress(blue);
        etBlue.setText(String.format(FORMAT_02X, blue));

        sbAlpha.setProgress(alpha);
        etAlpha.setText(String.format(FORMAT_02X, alpha));
    }

    private int toOriginal(float scaled) {
        return (int) (scaled / window.scale);
    }

    private float toScaled(int original) {
        return original * window.scale;
    }

    private void undoOrRedo(Bitmap bm) {
        optimizeSelection();
        bitmap.recycle();
        bitmap = Bitmap.createBitmap(bm.getWidth(), bm.getHeight(), Bitmap.Config.ARGB_8888);
        window.bitmap = bitmap;
        canvas = new Canvas(bitmap);
        canvas.drawBitmap(bm, 0.0f, 0.0f, opaquePaint);

        imageWidth = (int) toScaled(bitmap.getWidth());
        imageHeight = (int) toScaled(bitmap.getHeight());

        recycleBitmapIfIsNotNull(transformeeBitmap);
        transformeeBitmap = null;
        clearCanvasAndInvalidateView(previewCanvas, ivPreview);

        optimizeSelection();

        drawChessboardOnView();
        drawBitmapOnView();
        drawGridOnView();
        drawSelectionOnView();
    }
}