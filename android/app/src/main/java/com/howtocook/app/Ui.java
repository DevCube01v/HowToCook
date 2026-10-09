package com.howtocook.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;

/** Shared blue palette, rounded surfaces and scalable line icons. */
final class Ui {
    static final int BLUE = Color.rgb(37,99,235), INK = Color.rgb(20,39,71);
    static final int MUTED = Color.rgb(104,125,157), PAPER = Color.rgb(247,250,255);
    static final int PALE = Color.rgb(235,243,255), BORDER = Color.rgb(228,236,247);
    static int dp(Context context, float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    static GradientDrawable surface(Context context, int color, int radius, boolean border) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color);
        shape.setCornerRadius(dp(context, radius));
        if (border) shape.setStroke(dp(context, 1), BORDER);
        return shape;
    }
    static Drawable ripple(Context context, int color, int radius, boolean border) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(32,37,99,235)), surface(context, color, radius, border), surface(context, Color.WHITE, radius, false));
    }
    static String categoryIcon(String name) {
        switch (name) {
            case "素菜": return "leaf";
            case "荤菜": return "meat";
            case "水产": return "fish";
            case "早餐": return "egg";
            case "主食": return "bowl";
            case "汤与粥": return "soup";
            case "甜品": return "cake";
            case "饮品": return "drink";
            case "调味料": return "bottle";
            default: return "box";
        }
    }
    static final class Icon extends View {
        private String name;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shape = new Path(), strokePath = new Path();
        private int tint;
        private boolean filled;
        public Icon(Context context) { this(context, "book", BLUE); }
        Icon(Context context, String name, int tint) {
            super(context); this.name = name; this.tint = tint;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        void symbol(String value) { name = value; invalidate(); }
        void appearance(int color, boolean fill) { tint = color; filled = fill; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth()-getPaddingLeft()-getPaddingRight();
            float height = getHeight()-getPaddingTop()-getPaddingBottom();
            float size = Math.min(width, height);
            canvas.save(); canvas.translate(getPaddingLeft()+(width-size)/2, getPaddingTop()+(height-size)/2); canvas.scale(size/24, size/24);
            paint.setColor(tint); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.65f);
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            shape.rewind();
            switch (name) {
                case "search": canvas.drawCircle(10,10,6,paint); line(canvas,14.5f,14.5f,21,21); break;
                case "back": path(canvas,15,5,8,12,15,19); break;
                case "chevron": path(canvas,9,5,16,12,9,19); break;
                case "close": line(canvas,7,7,17,17); line(canvas,17,7,7,17); break;
                case "heart":
                    Path heart = shape; heart.moveTo(12,21); heart.cubicTo(9,18,2,13,2,7.8f);
                    heart.cubicTo(2,2,9,1,12,6); heart.cubicTo(15,1,22,2,22,7.8f); heart.cubicTo(22,13,15,18,12,21);
                    paint.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE); canvas.drawPath(heart,paint); break;
                case "info": canvas.drawCircle(12,12,9,paint); line(canvas,12,11,12,17); paint.setStyle(Paint.Style.FILL); canvas.drawCircle(12,7,1,paint); break;
                case "book":
                    path(canvas,12,5,5,3,3,4,3,19,5,18,12,20,19,18,21,19,21,4,19,3,12,5,12,20); break;
                case "leaf":
                    Path leaf = shape; leaf.moveTo(5,19); leaf.cubicTo(1,8,10,2,21,3); leaf.cubicTo(20,16,13,23,5,19); canvas.drawPath(leaf,paint);
                    line(canvas,4,21,17,8); path(canvas,8,12,9,16,14,16); break;
                case "meat":
                    canvas.save(); canvas.rotate(-30,12,12); canvas.drawOval(3,4,21,20,paint); canvas.drawOval(11,8,17,14,paint); canvas.drawArc(6,7,16,17,90,135,false,paint); canvas.restore(); break;
                case "fish":
                    Path fish = shape; fish.moveTo(3,12); fish.cubicTo(9,3,15,5,19,10); fish.lineTo(23,7); fish.lineTo(23,17); fish.lineTo(19,14); fish.cubicTo(14,20,8,21,3,12); canvas.drawPath(fish,paint);
                    line(canvas,10,7,12,3); line(canvas,10,17,12,21); paint.setStyle(Paint.Style.FILL); canvas.drawCircle(7,11,1,paint); break;
                case "egg":
                    Path egg = shape; egg.moveTo(12,2); egg.cubicTo(19,2,23,21,12,22); egg.cubicTo(1,21,5,2,12,2); canvas.drawPath(egg,paint);
                    canvas.drawCircle(12,13,4,paint); break;
                case "bowl":
                    canvas.drawOval(2,6,22,11,paint); canvas.drawArc(2,2,22,20,0,180,false,paint); line(canvas,9,21,15,21); break;
                case "soup":
                    path(canvas,2,11,4,18,8,21,16,21,20,18,22,11,2,11); line(canvas,8,7,10,3); line(canvas,13,7,15,2); line(canvas,18,7,20,3); break;
                case "cake":
                    path(canvas,6,12,7,22,17,22,18,12,6,12); canvas.drawArc(3,5,12,14,160,220,false,paint); canvas.drawArc(8,1,17,13,175,210,false,paint); canvas.drawArc(14,5,22,14,190,195,false,paint);
                    line(canvas,10,15,10,19); line(canvas,14,15,14,19); break;
                case "drink":
                    path(canvas,5,7,7,22,17,22,19,7,5,7); line(canvas,4,7,20,7); path(canvas,13,11,15,2,20,2); line(canvas,8,12,16,12); break;
                case "bottle":
                    canvas.drawRoundRect(9,2,15,6,1,1,paint); path(canvas,9,6,9,9,6,12,6,21,18,21,18,12,15,9,15,6); line(canvas,7,14,17,14); break;
                case "box":
                    path(canvas,5,3,19,3,21,8,21,21,3,21,3,8,5,3); line(canvas,3,8,21,8); path(canvas,8,14,11,17,17,11); break;
                case "kitchen":
                    paint.setStrokeWidth(1.1f); canvas.drawOval(1,12,18,16,paint); canvas.drawArc(1,8,18,23,0,180,false,paint); line(canvas,7,23,12,23);
                    Path steam = shape; steam.moveTo(5,10); steam.cubicTo(9,7,2,6,6,2); steam.moveTo(11,10); steam.cubicTo(15,7,8,5,12,1); canvas.drawPath(steam,paint);
                    canvas.drawOval(19,1,23,9,paint); line(canvas,21,9,19,23); break;
                default: canvas.drawCircle(12,12,8,paint);
            }
            canvas.restore();
        }
        private void line(Canvas c, float x1, float y1, float x2, float y2) { c.drawLine(x1,y1,x2,y2,paint); }
        private void path(Canvas c, float... points) {
            Path p = strokePath; p.rewind(); p.moveTo(points[0],points[1]);
            for (int i=2;i<points.length;i+=2) p.lineTo(points[i],points[i+1]);
            c.drawPath(p,paint);
        }
    }
}
