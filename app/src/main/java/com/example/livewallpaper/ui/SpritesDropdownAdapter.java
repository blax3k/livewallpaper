package com.example.livewallpaper.ui;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom adapter for sprite dropdown that includes a "+ sprite" button as the last item.
 */
public class SpritesDropdownAdapter extends ArrayAdapter<String> {
    private static final String TAG = "SpritesDropdownAdapter";
    private static final String ADD_SPRITE_ITEM = "+ sprite";
    private final List<String> items;
    private final Context context;

    public SpritesDropdownAdapter(@NonNull Context context, @NonNull List<String> spriteNames) {
        super(context, com.example.livewallpaper.R.layout.spinner_item, createItemsList(spriteNames));
        this.context = context;
        this.items = createItemsList(spriteNames);
    }

    /**
     * Create list of items including the "+ sprite" button at the end.
     */
    private static List<String> createItemsList(List<String> spriteNames) {
        List<String> items = new ArrayList<>(spriteNames);
        items.add(ADD_SPRITE_ITEM);
        return items;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return getCustomView(position, convertView, parent, false);
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return getCustomView(position, convertView, parent, true);
    }

    private View getCustomView(int position, View convertView, ViewGroup parent, boolean isDropdown) {
        if (convertView == null) {
            int layoutId = isDropdown ? com.example.livewallpaper.R.layout.spinner_dropdown_item : com.example.livewallpaper.R.layout.spinner_item;
            convertView = LayoutInflater.from(context).inflate(layoutId, parent, false);
        }

        TextView textView = convertView.findViewById(android.R.id.text1);
        String item = items.get(position);

        if (ADD_SPRITE_ITEM.equals(item)) {
            textView.setText(item);
            // You can optionally style this differently
            if (isDropdown) {
                textView.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
            }
        } else {
            textView.setText(item);
        }

        return convertView;
    }

    /**
     * Check if the given position is the "+ sprite" item.
     */
    public boolean isAddSpriteItem(int position) {
        return position == items.size() - 1;
    }

    /**
     * Get the actual sprite count (excluding the "+ sprite" item).
     */
    public int getSpriteCount() {
        return items.size() - 1;
    }

    /**
     * Update the sprite list and refresh the adapter.
     */
    public void updateSpriteList(List<String> spriteNames) {
        this.items.clear();
        this.items.addAll(createItemsList(spriteNames));
        notifyDataSetChanged();
    }
}
