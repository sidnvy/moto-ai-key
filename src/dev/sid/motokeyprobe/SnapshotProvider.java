package dev.sid.motokeyprobe;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

public final class SnapshotProvider extends ContentProvider {
  static final String AUTHORITY = "dev.sid.motokeyprobe.snapshot";
  static Uri uriFor(String filename) {
    return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(filename).build();
  }
  private File file(Uri uri) throws FileNotFoundException {
    String name = uri.getLastPathSegment();
    if (!AUTHORITY.equals(uri.getAuthority()) || name == null ||
        !name.matches("capture-[0-9]+-[0-9]+\\.png") ||
        uri.getPathSegments().size() != 1) throw new FileNotFoundException("Invalid capture");
    return new File(getContext().getCacheDir(), name);
  }
  @Override public boolean onCreate() { return true; }
  @Override public String getType(Uri uri) {
    try { file(uri); return "image/png"; } catch (FileNotFoundException e) { return null; }
  }
  @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
    return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
  }
  @Override public Cursor query(Uri uri, String[] projection, String s, String[] args, String order) {
    try {
      File f = file(uri);
      MatrixCursor rows = new MatrixCursor(new String[]{"_display_name", "_size"});
      rows.addRow(new Object[]{f.getName(), f.length()});
      return rows;
    } catch (FileNotFoundException e) { return null; }
  }
  @Override public Uri insert(Uri uri, ContentValues v) { throw new UnsupportedOperationException(); }
  @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
  @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
}
