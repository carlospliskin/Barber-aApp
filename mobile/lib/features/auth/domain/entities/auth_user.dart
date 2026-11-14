import 'package:freezed_annotation/freezed_annotation.dart';

part 'auth_user.freezed.dart';

// ─── Value objects ────────────────────────────────────────────────────────────

@freezed
class TenantInfo with _$TenantInfo {
  const TenantInfo._();

  const factory TenantInfo({
    required String id,
    required String name,
    @Default('America/Mexico_City') String timezone,
    @Default('FREE') String plan,
  }) = _TenantInfo;

  bool get isPaidPlan => plan != 'FREE';
}

@freezed
class ClientProfileInfo with _$ClientProfileInfo {
  const ClientProfileInfo._();

  const factory ClientProfileInfo({
    required String id,
    required String name,
    String? phone,
    @Default(false) bool isPenalized,
    String? penaltyUntil,
    @Default(0) int noShowCount,
  }) = _ClientProfileInfo;
}

// ─── AuthUser — entidad principal ─────────────────────────────────────────────

@freezed
class AuthUser with _$AuthUser {
  const AuthUser._();

  const factory AuthUser({
    required String userId,
    required String token,
    required String refreshToken,
    required List<String> capabilities,
    required String primaryRole,
    TenantInfo? tenant,
    ClientProfileInfo? clientProfile,
  }) = _AuthUser;

  // ─── Computed ────────────────────────────────────────────────────────────────
  bool get isOwner  => capabilities.contains('OWNER');
  bool get isBarber => capabilities.contains('BARBER');
  bool get isClient => capabilities.contains('CLIENT');
  bool get hasMultipleRoles => capabilities.length > 1;

  String get displayName =>
      clientProfile?.name ?? tenant?.name ?? userId.substring(0, 8);

  String get initials {
    final parts = displayName.trim().split(' ');
    if (parts.length >= 2) return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
    return displayName.isNotEmpty ? displayName[0].toUpperCase() : 'U';
  }
}

// ─── AuthMode — estado de navegación ─────────────────────────────────────────

enum AuthMode { loading, unauthenticated, modeSelection, owner, barber, client }

extension AuthModeX on AuthMode {
  bool get isAuthenticated => this != AuthMode.loading && this != AuthMode.unauthenticated;

  String get routePath => switch (this) {
    AuthMode.loading         => '/splash',
    AuthMode.unauthenticated => '/login',
    AuthMode.modeSelection   => '/select-mode',
    AuthMode.owner           => '/owner/home',
    AuthMode.barber          => '/barber/home',
    AuthMode.client          => '/client/home',
  };
}
