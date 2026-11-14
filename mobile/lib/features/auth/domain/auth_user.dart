import 'package:freezed_annotation/freezed_annotation.dart';

part 'auth_user.freezed.dart';
part 'auth_user.g.dart';

// ─── TenantInfo ───────────────────────────────────────────────────────────────

@freezed
class TenantInfo with _$TenantInfo {
  const factory TenantInfo({
    required String id,
    required String name,
    @Default('America/Mexico_City') String timezone,
    @Default('FREE') String plan,
  }) = _TenantInfo;

  factory TenantInfo.fromJson(Map<String, dynamic> json) =>
      _$TenantInfoFromJson(json);
}

// ─── ClientProfileInfo ────────────────────────────────────────────────────────

@freezed
class ClientProfileInfo with _$ClientProfileInfo {
  const factory ClientProfileInfo({
    required String id,
    required String name,
    String? phone,
    @Default(false) bool isPenalized,
    String? penaltyUntil,
    @Default(0) int noShowCount,
  }) = _ClientProfileInfo;

  factory ClientProfileInfo.fromJson(Map<String, dynamic> json) =>
      _$ClientProfileInfoFromJson(json);
}

// ─── AuthUser — entidad principal ─────────────────────────────────────────────

@freezed
class AuthUser with _$AuthUser {
  const AuthUser._(); // permite métodos custom

  const factory AuthUser({
    required String userId,
    required String token,
    required String refreshToken,
    required List<String> capabilities,
    required String primaryRole,
    TenantInfo? tenant,
    ClientProfileInfo? clientProfile,
  }) = _AuthUser;

  factory AuthUser.fromJson(Map<String, dynamic> json) =>
      _$AuthUserFromJson(json);

  // ─── Computed properties ───────────────────────────────────────────────────

  bool get isOwner => capabilities.contains('OWNER');
  bool get isBarber => capabilities.contains('BARBER');
  bool get isClient => capabilities.contains('CLIENT');

  /// Tiene más de un rol → debe ver el selector de modo
  bool get hasMultipleRoles => capabilities.length > 1;

  /// Nombre para mostrar en UI
  String get displayName =>
      clientProfile?.name ??
      tenant?.name ??
      userId.substring(0, 8);

  /// Primera inicial para el avatar
  String get initials {
    final name = displayName;
    final parts = name.trim().split(' ');
    if (parts.length >= 2) {
      return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
    }
    return name.isNotEmpty ? name[0].toUpperCase() : 'U';
  }

  bool get isPenalized => clientProfile?.isPenalized ?? false;
}

// ─── AuthMode — enum de estado de navegación ──────────────────────────────────

enum AuthMode {
  loading,
  unauthenticated,
  /// Owner o Barber deben elegir con qué rol entran
  modeSelection,
  owner,
  barber,
  client,
}

extension AuthModeX on AuthMode {
  bool get isAuthenticated => switch (this) {
    AuthMode.loading || AuthMode.unauthenticated => false,
    _ => true,
  };

  String get homePath => switch (this) {
    AuthMode.owner => '/owner/home',
    AuthMode.barber => '/barber/home',
    AuthMode.client => '/client/home',
    AuthMode.modeSelection => '/select-mode',
    AuthMode.unauthenticated => '/login',
    AuthMode.loading => '/splash',
  };
}
