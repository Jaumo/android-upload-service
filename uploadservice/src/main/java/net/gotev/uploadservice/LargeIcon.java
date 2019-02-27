package net.gotev.uploadservice;

import android.os.Parcel;
import android.os.Parcelable;

public class LargeIcon implements Parcelable {
    private String path;
    private float width;
    private float height;

    public LargeIcon(String path, float width, float height) {
        this.path = path;
        this.width = width;
        this.height = height;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    protected LargeIcon(Parcel in) {
        path = in.readString();
        width = in.readFloat();
        height = in.readFloat();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(path);
        dest.writeFloat(width);
        dest.writeFloat(height);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<LargeIcon> CREATOR = new Parcelable.Creator<LargeIcon>() {
        @Override
        public LargeIcon createFromParcel(Parcel in) {
            return new LargeIcon(in);
        }

        @Override
        public LargeIcon[] newArray(int size) {
            return new LargeIcon[size];
        }
    };
}