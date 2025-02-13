package mega.privacy.android.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mega.privacy.android.domain.repository.AccountRepository
import mega.privacy.android.domain.repository.VerificationRepository
import mega.privacy.android.domain.usecase.AreAccountAchievementsEnabled
import mega.privacy.android.domain.usecase.GetCountryCallingCodes
import mega.privacy.android.domain.usecase.GetCurrentCountryCode
import mega.privacy.android.domain.usecase.IsSMSVerificationShown
import mega.privacy.android.domain.usecase.Logout
import mega.privacy.android.domain.usecase.ResetSMSVerifiedPhoneNumber
import mega.privacy.android.domain.usecase.SendSMSVerificationCode
import mega.privacy.android.domain.usecase.SetSMSVerificationShown

/**
 * SMS verification Module
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SMSVerificationModule {

    companion object {
        /**
         * Provides the Use Case [SetSMSVerificationShown]
         */
        @Provides
        fun provideSetSMSVerificationShown(repository: VerificationRepository): SetSMSVerificationShown =
            SetSMSVerificationShown(repository::setSMSVerificationShown)

        /**
         * Provides the Use Case [IsSMSVerificationShown]
         */
        @Provides
        fun provideIsSMSVerificationShown(repository: VerificationRepository): IsSMSVerificationShown =
            IsSMSVerificationShown(repository::isSMSVerificationShown)

        /**
         * Provides the Use Case [GetCountryCallingCodes]
         */
        @Provides
        fun provideGetCountryCallingCodes(repository: VerificationRepository): GetCountryCallingCodes =
            GetCountryCallingCodes(repository::getCountryCallingCodes)

        /**
         * Provides the Use Case [SendSMSVerificationCode]
         */
        @Provides
        fun provideSendSMSVerificationCode(repository: VerificationRepository): SendSMSVerificationCode =
            SendSMSVerificationCode(repository::sendSMSVerificationCode)

        /**
         * Provides the Use Case [ResetSMSVerifiedPhoneNumber]
         */
        @Provides
        fun provideResetSMSVerifiedPhoneNumber(repository: VerificationRepository): ResetSMSVerifiedPhoneNumber =
            ResetSMSVerifiedPhoneNumber(repository::resetSMSVerifiedPhoneNumber)

        /**
         * Provides the Use Case [AreAccountAchievementsEnabled]
         */
        @Provides
        fun provideAreAccountAchievementsEnabled(repository: AccountRepository): AreAccountAchievementsEnabled =
            AreAccountAchievementsEnabled(repository::areAccountAchievementsEnabled)

        /**
         * Provides the Use Case [GetCurrentCountryCode]
         */
        @Provides
        fun provideGetCurrentCountryCode(repository: VerificationRepository): GetCurrentCountryCode =
            GetCurrentCountryCode(repository::getCurrentCountryCode)

        /**
         * Provides the Use Case [Logout]
         */
        @Provides
        fun provideLogout(repository: AccountRepository): Logout = Logout(repository::logout)
    }
}
