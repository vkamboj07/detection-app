package com.example.billboardanalytics.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;

@Database(entities = {DeviceEntity.class, ObservationEntity.class, SessionEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract AnalyticsDao analyticsDao();

    private static volatile AppDatabase INSTANCE;

    /**
     * Placeholder for future schema migrations.
     * When the database version is incremented, add a new Migration object here.
     *
     * Example:
     *   static final Migration MIGRATION_1_2 = new Migration(1, 2) {
     *       @Override
     *       public void migrate(@NonNull SupportSQLiteDatabase database) {
     *           database.execSQL("ALTER TABLE devices ADD COLUMN alias TEXT");
     *       }
     *   };
     */
    private static final Migration[] ALL_MIGRATIONS = {
        // Add new migrations here as the schema evolves, e.g. MIGRATION_1_2
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "billboard_analytics_db")
                            .addMigrations(ALL_MIGRATIONS)
                            // fallbackToDestructiveMigration removed — add explicit Migration
                            // objects above instead of silently wiping data on schema changes.
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
