package com.example.myecowatch1.data.local;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.myecowatch1.data.models.Announcement;
import com.example.myecowatch1.data.models.Report;
import com.example.myecowatch1.data.models.User;
import com.example.myecowatch1.data.models.History;
import com.example.myecowatch1.data.models.HistoryTombstone;
import com.example.myecowatch1.data.models.Notification;
import com.example.myecowatch1.data.models.BiometricSetting;
import com.example.myecowatch1.data.models.NotificationSetting;

@TypeConverters({StringListConverter.class})
@Database(
        entities = {
                User.class,
                Report.class,
                History.class,
                HistoryTombstone.class,
                Notification.class,
                BiometricSetting.class,
                NotificationSetting.class,
                Announcement.class
        },
        version = 30,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE notifications ADD COLUMN reportId TEXT");
            database.execSQL("ALTER TABLE notifications ADD COLUMN deletedAt TEXT");

            // Older polling used the local auto-generated Room ID as a fallback
            // server key, which can create duplicate-looking rows. Preserve the
            // newest copy before adding the account-scoped unique index.
            database.execSQL(
                    "DELETE FROM notifications WHERE mongoId IS NOT NULL "
                            + "AND residentId IS NOT NULL AND id NOT IN ("
                            + "SELECT MAX(id) FROM notifications "
                            + "WHERE mongoId IS NOT NULL AND residentId IS NOT NULL "
                            + "GROUP BY residentId, mongoId)"
            );
            database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_notifications_residentId_mongoId "
                            + "ON notifications(residentId, mongoId)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notifications_residentId_reportId "
                            + "ON notifications(residentId, reportId)"
            );
        }
    };

    private static volatile AppDatabase instance;

    /**
     * Durable tombstones for permanently deleted history logs. Without them a
     * refresh re-imports any server row the local cache no longer holds, which
     * resurrected deleted entries on every pull-to-refresh.
     */
    private static final Migration MIGRATION_20_21 = new Migration(20, 21) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `history_tombstones` ("
                            + "`mongoId` TEXT NOT NULL, "
                            + "`residentId` TEXT, "
                            + "`deletedAt` INTEGER NOT NULL, "
                            + "PRIMARY KEY(`mongoId`))"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_history_tombstones_residentId "
                            + "ON history_tombstones(residentId)"
            );
        }
    };

    /**
     * Adds the resident announcement feed. Existing report, history and
     * notification data is preserved, and the unique index on announcementId
     * makes a re-sync replace a row instead of duplicating it.
     */
    private static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `announcements` ("
                            + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                            + "`announcementId` TEXT, "
                            + "`title` TEXT, "
                            + "`message` TEXT, "
                            + "`audience` TEXT, "
                            + "`barangay` TEXT, "
                            + "`targetResidentIds` TEXT, "
                            + "`priority` TEXT, "
                            + "`createdByAdminId` TEXT, "
                            + "`createdAt` TEXT)"
            );
            database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_announcements_announcementId "
                            + "ON announcements(announcementId)"
            );
        }
    };

    /**
     * Ensures the history_tombstones table schema matches the entity definition.
     * Older databases may have been created without the residentId index.
     */
    private static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Ensure the index exists on history_tombstones
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_history_tombstones_residentId "
                            + "ON history_tombstones(residentId)"
            );
        }
    };

    /**
     * Ensures the reports table has clusterId and history_tombstones schema matches.
     */
    private static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            ensureColumnExists(database, "reports", "clusterId", "TEXT");
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `history_tombstones` ("
                            + "`mongoId` TEXT NOT NULL, "
                            + "`residentId` TEXT, "
                            + "`deletedAt` INTEGER NOT NULL, "
                            + "PRIMARY KEY(`mongoId`))"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_history_tombstones_residentId "
                            + "ON history_tombstones(residentId)"
            );
        }
    };

    /**
     * Ensures reports.clusterId and history_tombstones are present for version 24 -> 25 upgrades.
     */
    private static final Migration MIGRATION_24_25 = new Migration(24, 25) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            ensureColumnExists(database, "reports", "clusterId", "TEXT");
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `history_tombstones` ("
                            + "`mongoId` TEXT NOT NULL, "
                            + "`residentId` TEXT, "
                            + "`deletedAt` INTEGER NOT NULL, "
                            + "PRIMARY KEY(`mongoId`))"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_history_tombstones_residentId "
                            + "ON history_tombstones(residentId)"
            );
        }
    };

    /**
     * Adds the multi-photo column. Existing rows keep only the cover image and
     * are repopulated on the next sync, so the upgrade never discards data.
     */
    private static final Migration MIGRATION_25_26 = new Migration(25, 26) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            ensureColumnExists(database, "reports", "photoUrls", "TEXT");
        }
    };

    /**
     * Adds the contact columns the account screen edits and the admin app reads.
     * Both are nullable, so a cached user that predates them upgrades in place and
     * is repopulated from the server on the next fetch.
     */
    private static final Migration MIGRATION_26_27 = new Migration(26, 27) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            ensureColumnExists(database, "users", "phoneNumber", "TEXT");
            ensureColumnExists(database, "users", "messengerLink", "TEXT");
        }
    };

    /**
     * Adds the assignment and cleaner-response columns the report details dialog
     * reads.
     *
     * <p>All nullable, so a cached report that predates them upgrades in place
     * and is repopulated from the server on the next fetch. The dialog shows
     * nothing for a row that never received an assignment, which is exactly what
     * an upgraded cache should look like until the sync lands.
     */
    private static final Migration MIGRATION_27_28 = new Migration(27, 28) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            ensureColumnExists(database, "reports", "personnelResponse", "TEXT");
            ensureColumnExists(database, "reports", "assignedPersonnelId", "TEXT");
            ensureColumnExists(database, "reports", "assignedPersonnelIds", "TEXT");
            ensureColumnExists(database, "reports", "assignedAt", "TEXT");
            ensureColumnExists(database, "reports", "handledById", "TEXT");
            ensureColumnExists(database, "reports", "resolvedAt", "TEXT");
        }
    };

    /**
     * Adds the waste scale column the report form picks and the details dialog
     * shows.
     *
     * <p>Nullable, so a cached report that predates it upgrades in place. A row
     * with no scale reads as the small scale, which is the only size a report
     * could have carried before the field existed.
     */
    private static final Migration MIGRATION_28_29 = new Migration(28, 29) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            ensureColumnExists(database, "reports", "wasteScale", "TEXT");
        }
    };

    /**
     * Adds the dispatched crew and collection vehicle the details dialog shows.
     *
     * <p>All nullable, so a cached report that predates them upgrades in place
     * and is repopulated from the server on the next fetch. A row with no crew
     * reads as "nobody has been dispatched yet", which is exactly what it is.
     */
    private static final Migration MIGRATION_29_30 = new Migration(29, 30) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            ensureColumnExists(database, "reports", "assignedTeamId", "TEXT");
            ensureColumnExists(database, "reports", "assignedTeamName", "TEXT");
            ensureColumnExists(database, "reports", "assignedVehicleId", "TEXT");
            ensureColumnExists(database, "reports", "assignedVehiclePlateNumber", "TEXT");
            ensureColumnExists(database, "reports", "assignedVehicleType", "TEXT");
        }
    };

    private static void ensureColumnExists(@NonNull SupportSQLiteDatabase database, String tableName, String columnName, String columnDef) {        try (Cursor cursor = database.query("PRAGMA table_info(`" + tableName + "`)")) {
            boolean exists = false;
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (nameIndex != -1 && columnName.equalsIgnoreCase(cursor.getString(nameIndex))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                database.execSQL("ALTER TABLE `" + tableName + "` ADD COLUMN `" + columnName + "` " + columnDef);
            }
        } catch (Exception e) {
            Log.e("AppDatabase", "Error ensuring column " + columnName + " on " + tableName, e);
        }
    }

    public abstract UserDao userDao();
    public abstract AnnouncementDao announcementDao();
    public abstract ReportDao reportDao();
    public abstract HistoryDao historyDao();
    public abstract HistoryTombstoneDao historyTombstoneDao();
    public abstract NotificationDao notificationDao();
    public abstract BiometricDao biometricDao();
    public abstract NotificationSettingDao notificationSettingDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "ecowatch_db"
                            )
                            .addMigrations(
                                    MIGRATION_19_20,
                                    MIGRATION_20_21,
                                    MIGRATION_21_22,
                                    MIGRATION_22_23,
                                    MIGRATION_23_24,
                                    MIGRATION_24_25,
                                    MIGRATION_25_26,
                                    MIGRATION_26_27,
                                    MIGRATION_27_28,
                                    MIGRATION_28_29,
                                    MIGRATION_29_30
                            )
                            // Preserve notification/report/settings data during schema upgrades.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
