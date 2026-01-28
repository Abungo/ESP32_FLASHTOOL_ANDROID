package com.abungo.usbserial.Flash;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.abungo.usbserial.R;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FirmwareAdapter extends RecyclerView.Adapter<FirmwareAdapter.ViewHolder> {

    private final List<File> firmwareFiles;
    private final OnFirmwareActionListener listener;

    public interface OnFirmwareActionListener {
        void onDelete(File file);

        void onItemClick(File file);
    }

    public FirmwareAdapter(List<File> firmwareFiles, OnFirmwareActionListener listener) {
        this.firmwareFiles = firmwareFiles;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_firmware, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = firmwareFiles.get(position);
        holder.tvFileName.setText(file.getName());

        long sizeBytes = file.length();
        String size = sizeBytes < 1024 * 1024 ? String.format(Locale.getDefault(), "%.1f KB", sizeBytes / 1024f)
                : String.format(Locale.getDefault(), "%.1f MB", sizeBytes / (1024f * 1024f));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String date = sdf.format(new Date(file.lastModified()));

        holder.tvFileInfo.setText(size + " | " + date);

        holder.btnDelete.setOnClickListener(v -> listener.onDelete(file));
        holder.itemView.setOnClickListener(v -> listener.onItemClick(file));
    }

    @Override
    public int getItemCount() {
        return firmwareFiles.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvFileName;
        TextView tvFileInfo;
        View btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFileInfo = itemView.findViewById(R.id.tvFileInfo);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
