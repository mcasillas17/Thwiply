package thwiply.elopenmike.com.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import thwiply.elopenmike.com.data.local.MIGRATION_1_2
import thwiply.elopenmike.com.data.local.THWIPLY_DATABASE_NAME
import thwiply.elopenmike.com.data.local.ThwiplyDatabase
import thwiply.elopenmike.com.data.local.dao.CorrectionDao
import thwiply.elopenmike.com.data.local.dao.DataLifecycleDao
import thwiply.elopenmike.com.data.local.dao.TriageDao
import thwiply.elopenmike.com.data.local.dao.UserRuleDao
import thwiply.elopenmike.com.data.repository.RoomCorrectionRepository
import thwiply.elopenmike.com.data.repository.RoomNotificationDataLifecycleRepository
import thwiply.elopenmike.com.data.repository.RoomTriageRepository
import thwiply.elopenmike.com.data.repository.RoomUserRuleRepository
import thwiply.elopenmike.com.domain.triage.CorrectionRepository
import thwiply.elopenmike.com.domain.triage.NotificationDataLifecycleRepository
import thwiply.elopenmike.com.domain.triage.TriageRepository
import thwiply.elopenmike.com.domain.triage.UserRuleRepository

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ThwiplyDatabase =
        Room.databaseBuilder(
            context,
            ThwiplyDatabase::class.java,
            THWIPLY_DATABASE_NAME,
        )
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideTriageDao(database: ThwiplyDatabase): TriageDao = database.triageDao()

    @Provides
    fun provideCorrectionDao(database: ThwiplyDatabase): CorrectionDao = database.correctionDao()

    @Provides
    fun provideUserRuleDao(database: ThwiplyDatabase): UserRuleDao = database.userRuleDao()

    @Provides
    fun provideDataLifecycleDao(database: ThwiplyDatabase): DataLifecycleDao =
        database.dataLifecycleDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTriageRepository(
        repository: RoomTriageRepository,
    ): TriageRepository

    @Binds
    @Singleton
    abstract fun bindCorrectionRepository(
        repository: RoomCorrectionRepository,
    ): CorrectionRepository

    @Binds
    @Singleton
    abstract fun bindUserRuleRepository(
        repository: RoomUserRuleRepository,
    ): UserRuleRepository

    @Binds
    @Singleton
    abstract fun bindNotificationDataLifecycleRepository(
        repository: RoomNotificationDataLifecycleRepository,
    ): NotificationDataLifecycleRepository
}
