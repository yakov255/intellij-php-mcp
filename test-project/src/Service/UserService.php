<?php

namespace App\Service;

use App\Contract\ServiceInterface;
use App\Trait\LoggableTrait;

class UserService implements ServiceInterface
{
    use LoggableTrait;

    private int $userId;

    public function __construct(int $userId)
    {
        $this->userId = $userId;
    }

    public function execute(string $input): string
    {
        $this->log("Processing input for user #{$this->userId}");
        return "User #{$this->userId}: $input";
    }

    public function getName(): string
    {
        return "UserService#{$this->userId}";
    }

    public function getUserId(): int
    {
        return $this->userId;
    }
}
