package com.example.livewallpaper.scene;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Configuration class for creating sprites with all necessary parameters.
 * Used both for JSON deserialization (fields populated by GSON) and
 * programmatic sprite creation (via constructor).
 * Implements Parcelable for passing through Intent extras.
 */
public class SpriteData implements Parcelable {
    // Fields populated by GSON during JSON deserialization
    public String textureResource;
    public float width;
    public float height;
    public float parallaxMultiplier;
    public float positionX;
    public float positionY;
    public float[] texCoordinates;  // The ONLY texture state that matters

    // Set after resource resolution
    public int textureResourceId;
    public String name;

    public SpriteData() {
    }

    protected SpriteData(Parcel in) {
        textureResource = in.readString();
        width = in.readFloat();
        height = in.readFloat();
        parallaxMultiplier = in.readFloat();
        positionX = in.readFloat();
        positionY = in.readFloat();
        texCoordinates = in.createFloatArray();
        textureResourceId = in.readInt();
        name = in.readString();
    }

    public static final Creator<SpriteData> CREATOR = new Creator<SpriteData>() {
        @Override
        public SpriteData createFromParcel(Parcel in) {
            return new SpriteData(in);
        }

        @Override
        public SpriteData[] newArray(int size) {
            return new SpriteData[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(textureResource);
        dest.writeFloat(width);
        dest.writeFloat(height);
        dest.writeFloat(parallaxMultiplier);
        dest.writeFloat(positionX);
        dest.writeFloat(positionY);
        dest.writeFloatArray(texCoordinates);
        dest.writeInt(textureResourceId);
        dest.writeString(name);
    }
}

